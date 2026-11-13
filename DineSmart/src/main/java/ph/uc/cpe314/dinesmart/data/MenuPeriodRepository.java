package ph.uc.cpe314.dinesmart.data;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import ph.uc.cpe314.dinesmart.util.Money;

/** Date-based meal periods and seasonal menu scheduling. */
public final class MenuPeriodRepository {
    public record Period(long id, String dish, String period, String startsOn, String endsOn, boolean active) {
        @Override public String toString() { return dish + " · " + period + " · " + startsOn + " to " + endsOn + (active ? "" : " · INACTIVE"); }
    }
    public void schedule(String dishName, String period, String startsOn, String endsOn) throws SQLException {
        if (dishName == null || dishName.isBlank()) { throw new IllegalArgumentException("Choose a dish."); }
        if (!List.of("BREAKFAST","LUNCH","DINNER","SEASONAL","SPECIAL").contains(period)) { throw new IllegalArgumentException("Invalid menu period."); }
        if (startsOn == null || endsOn == null || endsOn.compareTo(startsOn) < 0) { throw new IllegalArgumentException("End date must be on or after start date."); }
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(
            "INSERT INTO menu_periods(menu_item_id,period_name,starts_on,ends_on,active) SELECT menu_item_id,?,?,?,1 FROM menu_items WHERE name=?")) {
            ps.setString(1,period); ps.setString(2,startsOn); ps.setString(3,endsOn); ps.setString(4,dishName.trim());
            if(ps.executeUpdate()==0) { throw new SQLException("Dish not found or schedule could not be saved."); }
        }
    }

    /** Creates the sellable menu item only when an admin schedules a library recipe. */
    public void scheduleRecipe(long recipeId, long priceCents, int prepMinutes, String period, String startsOn, String endsOn) throws SQLException {
        if (priceCents <= 0) { throw new IllegalArgumentException("Enter a selling price greater than zero."); }
        if (prepMinutes < 0) { throw new IllegalArgumentException("Preparation time cannot be negative."); }
        if (!List.of("BREAKFAST","LUNCH","DINNER","SEASONAL","SPECIAL").contains(period)) { throw new IllegalArgumentException("Invalid menu period."); }
        try { java.time.LocalDate start=java.time.LocalDate.parse(startsOn); java.time.LocalDate end=java.time.LocalDate.parse(endsOn); if(end.isBefore(start)) throw new IllegalArgumentException("End date must be on or after start date."); }
        catch (java.time.format.DateTimeParseException ex) { throw new IllegalArgumentException("Use dates in YYYY-MM-DD format.", ex); }
        try (Connection c=Database.connect()) {
            c.setAutoCommit(false);
            try {
                String name, category, externalId;
                try (PreparedStatement find=c.prepareStatement("SELECT name,category,external_id FROM recipe_catalog WHERE recipe_id=?")) {
                    find.setLong(1,recipeId); try(ResultSet rs=find.executeQuery()) { if(!rs.next()) throw new SQLException("Select a recipe from the library."); name=rs.getString(1); category=rs.getString(2); externalId=rs.getString(3); }
                }
                try (PreparedStatement upsert=c.prepareStatement("""
                    INSERT INTO menu_items(name,item_type,price,price_cents,base_prep_minutes,api_meal_id,available_today,daily_menu_date)
                    VALUES(?,?,CAST(? AS REAL),?,?,?,0,NULL)
                    ON CONFLICT(name) DO UPDATE SET item_type=excluded.item_type,
                      price=excluded.price,price_cents=excluded.price_cents,
                      base_prep_minutes=excluded.base_prep_minutes,api_meal_id=excluded.api_meal_id,
                      available_today=0,daily_menu_date=NULL,is_archived=0
                    """)) {
                    upsert.setString(1,name); upsert.setString(2,category);
                    upsert.setString(3,Money.toDatabaseDecimal(priceCents));
                    upsert.setLong(4,priceCents); upsert.setInt(5,prepMinutes);
                    upsert.setString(6,externalId); upsert.executeUpdate();
                }
                long menuItemId;
                try(PreparedStatement find=c.prepareStatement("SELECT menu_item_id FROM menu_items WHERE name=?")) { find.setString(1,name); try(ResultSet rs=find.executeQuery()) { rs.next(); menuItemId=rs.getLong(1); } }
                // Rebuild this menu item's deduction map from the current saved recipe.
                // This prevents stale numeric mappings from deducting stock for a newer
                // descriptive measurement such as "1 handful" or "to taste".
                try (PreparedStatement clearLinks = c.prepareStatement(
                        "DELETE FROM menu_item_ingredients WHERE menu_item_id=?")) {
                    clearLinks.setLong(1, menuItemId);
                    clearLinks.executeUpdate();
                }
                // Copy only numeric, compatible recipe requirements into the inventory ledger.
                try (PreparedStatement requirements=c.prepareStatement("SELECT ingredient_name,quantity,unit,parse_status FROM recipe_ingredients WHERE recipe_id=?")) {
                    requirements.setLong(1,recipeId);
                    try(ResultSet rs=requirements.executeQuery()) {
                        while(rs.next()) {
                            String ingredientName=rs.getString("ingredient_name");
                            double quantity=rs.getDouble("quantity");
                            Double parsedQuantity=rs.wasNull()?null:quantity;
                            String unit=rs.getString("unit");
                            // A recipe is always schedulable when its original measurement is saved.
                            // Only numeric, recognized measurements get an inventory deduction link.
                            if (!"READY".equals(rs.getString("parse_status"))
                                    || !RecipeMeasureParser.isAutomaticallyDeductible(
                                            parsedQuantity, unit)) {
                                continue;
                            }
                            try(PreparedStatement addIngredient=c.prepareStatement("INSERT INTO ingredients(name,stock_quantity,stock_unit) VALUES(?,0,?) ON CONFLICT(name) DO NOTHING")) {
                                addIngredient.setString(1,ingredientName); addIngredient.setString(2,unit); addIngredient.executeUpdate();
                            }
                            long ingredientId; String stockUnit;
                            try(PreparedStatement findIngredient=c.prepareStatement("SELECT ingredient_id,stock_unit FROM ingredients WHERE name=? COLLATE NOCASE")) {
                                findIngredient.setString(1,ingredientName);
                                try(ResultSet ir=findIngredient.executeQuery()) {
                                    if(!ir.next()) { throw new SQLException("Could not create inventory ingredient: "+ingredientName); }
                                    ingredientId=ir.getLong(1); stockUnit=ir.getString(2);
                                }
                            }
                            double inventoryQuantity;
                            if (unit.equalsIgnoreCase(stockUnit)) {
                                inventoryQuantity = quantity;
                            } else if (RecipeMeasureParser.canConvert(unit, stockUnit)) {
                                inventoryQuantity = RecipeMeasureParser.convert(quantity, unit, stockUnit);
                            } else {
                                // Keep the recipe schedulable. Do not invent a conversion or deduct
                                // stock when descriptive/non-compatible measures are used.
                                continue;
                            }
                            try(PreparedStatement link=c.prepareStatement("""
                                INSERT INTO menu_item_ingredients(menu_item_id,ingredient_id,quantity_required,measure)
                                VALUES(?,?,?,?) ON CONFLICT(menu_item_id,ingredient_id) DO UPDATE SET quantity_required=excluded.quantity_required,measure=excluded.measure
                                """)) {
                                link.setLong(1,menuItemId); link.setLong(2,ingredientId); link.setDouble(3,inventoryQuantity); link.setString(4,stockUnit); link.executeUpdate();
                            }
                        }
                    }
                }
                try(PreparedStatement add=c.prepareStatement("INSERT INTO menu_periods(menu_item_id,period_name,starts_on,ends_on,active) VALUES(?,?,?,?,1)")) {
                    add.setLong(1,menuItemId); add.setString(2,period); add.setString(3,startsOn); add.setString(4,endsOn); add.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                try {
                    c.rollback();
                } catch (SQLException rollbackFailure) {
                    ex.addSuppressed(rollbackFailure);
                }
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public List<Period> findAll() throws SQLException {
        List<Period> rows=new ArrayList<>();
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(
            "SELECT p.period_id,m.name,p.period_name,p.starts_on,p.ends_on,p.active FROM menu_periods p JOIN menu_items m ON m.menu_item_id=p.menu_item_id ORDER BY p.starts_on DESC,m.name"); ResultSet rs=ps.executeQuery()) {
            while(rs.next()) { rows.add(new Period(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6)==1)); }
        }
        return rows;
    }
    public void setActive(long id, boolean active) throws SQLException {
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("UPDATE menu_periods SET active=? WHERE period_id=?")) {
            ps.setInt(1,active?1:0); ps.setLong(2,id); ps.executeUpdate();
        }
    }
}
