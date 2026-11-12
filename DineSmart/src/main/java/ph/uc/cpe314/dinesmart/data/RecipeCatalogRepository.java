package ph.uc.cpe314.dinesmart.data;

import ph.uc.cpe314.dinesmart.model.MealRecipe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Keeps recipe ideas separate from the sellable menu and stores structured ingredient quantities. */
public final class RecipeCatalogRepository {
    public record Recipe(long id, String source, String externalId, String name,
                         String category, String area, String instructions,
                         String thumbnailUrl, String ingredientsText) {
        @Override public String toString() {
            return name + "  ·  " + ("THEMEALDB".equals(source) ? "TheMealDB" : "Admin recipe") + "  ·  " + category;
        }
    }

    public record IngredientRequirement(String name, String sourceMeasure, Double quantity, String unit, String status) {
        @Override public String toString() {
            return name + "  ·  " + (quantity == null ? sourceMeasure : quantity + " " + unit);
        }
    }

    public List<IngredientRequirement> findIngredients(long recipeId) throws SQLException {
        List<IngredientRequirement> rows = new ArrayList<>();
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(
                "SELECT ingredient_name,source_measure,quantity,unit,parse_status FROM recipe_ingredients WHERE recipe_id=? ORDER BY ingredient_name")) {
            ps.setLong(1, recipeId);
            try (ResultSet rs=ps.executeQuery()) {
                while (rs.next()) {
                    double q=rs.getDouble("quantity"); Double quantity=rs.wasNull()?null:q;
                    rows.add(new IngredientRequirement(rs.getString("ingredient_name"),rs.getString("source_measure"),quantity,rs.getString("unit"),rs.getString("parse_status")));
                }
            }
        }
        return rows;
    }

    public record MissingIngredientResult(int added, int alreadyPresent, int needsReview,
                                          List<String> reviewNames, List<String> unitConflicts) {}

    /**
     * Adds every recipe ingredient to the inventory catalog with zero stock.
     * Descriptive measures are accepted and receive a neutral "unit" stock unit;
     * they are not linked for automatic deduction unless a numeric compatible measure exists.
     * Existing physical stock is never changed.
     */
    public MissingIngredientResult addMissingInventoryIngredientsDetailed(long recipeId) throws SQLException {
        Database.initialize();
        int added = 0, alreadyPresent = 0;
        List<String> reviewNames = new ArrayList<>();
        List<String> unitConflicts = new ArrayList<>();
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement read = c.prepareStatement(
                    "SELECT ingredient_name,unit FROM recipe_ingredients WHERE recipe_id=? ORDER BY ingredient_name")) {
                read.setLong(1, recipeId);
                try (ResultSet rs = read.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("ingredient_name");
                        String unit = rs.getString("unit");
                        String stockUnit = (unit == null || unit.isBlank()) ? "unit" : unit;
                        try (PreparedStatement find = c.prepareStatement(
                                "SELECT ingredient_id FROM ingredients WHERE name=? COLLATE NOCASE")) {
                            find.setString(1, name.trim());
                            try (ResultSet found = find.executeQuery()) {
                                if (found.next()) {
                                    alreadyPresent++;
                                    continue;
                                }
                            }
                        }
                        try (PreparedStatement insert = c.prepareStatement(
                                "INSERT INTO ingredients(name,stock_quantity,stock_unit) VALUES(?,0,?)")) {
                            insert.setString(1, name.trim());
                            insert.setString(2, stockUnit);
                            insert.executeUpdate();
                            added++;
                        }
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return new MissingIngredientResult(added, alreadyPresent, 0,
                List.copyOf(reviewNames), List.copyOf(unitConflicts));
    }

    /** Backward-compatible count-only method. */
    public int addMissingInventoryIngredients(long recipeId) throws SQLException {
        return addMissingInventoryIngredientsDetailed(recipeId).added();
    }

    /** Saves an admin-reviewed quantity/unit for one ingredient in a library recipe. */
    public void updateIngredient(long recipeId, String ingredientName, double quantity, String unit) throws SQLException {
        if (ingredientName == null || ingredientName.isBlank()
                || !Double.isFinite(quantity) || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Choose an ingredient and enter a quantity greater than zero.");
        }
        RecipeMeasureParser.Parsed parsed=RecipeMeasureParser.parse(quantity+" "+unit);
        if (parsed==null) { throw new IllegalArgumentException("Choose a supported unit."); }
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(
                "UPDATE recipe_ingredients SET source_measure=?,quantity=?,unit=?,parse_status='READY' WHERE recipe_id=? AND ingredient_name=?")) {
            ps.setString(1,quantity+" "+parsed.unit()); ps.setDouble(2,quantity); ps.setString(3,parsed.unit()); ps.setLong(4,recipeId); ps.setString(5,ingredientName);
            if (ps.executeUpdate()!=1) { throw new SQLException("Recipe ingredient was not found. Refresh the recipe first."); }
        }
    }

    public void saveMealDb(MealRecipe meal) throws SQLException {
        if (meal == null || meal.id() == null || meal.id().isBlank()
                || meal.name() == null || meal.name().isBlank()) {
            throw new IllegalArgumentException("Choose a valid TheMealDB recipe first.");
        }

        // Collapse repeated ingredient names before writing. TheMealDB can list the
        // same ingredient more than once; recipe_ingredients has one row per name.
        List<ManualIngredient> normalized = normalizeMealDbIngredients(meal.ingredients());
        StringBuilder display = new StringBuilder();
        for (MealRecipe.Ingredient item : meal.ingredients()) {
            if (!item.name().isBlank()) {
                display.append(item.name()).append(" — ")
                        .append(item.measure()).append("\n");
            }
        }

        Database.initialize();
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                long id = save(c, "THEMEALDB", meal.id(), meal.name().trim(), mapCategory(meal.category()),
                        meal.area(), meal.instructions(), meal.thumbnailUrl(), display.toString());

                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM recipe_ingredients WHERE recipe_id=?")) {
                    del.setLong(1, id);
                    del.executeUpdate();
                }
                for (ManualIngredient item : normalized) {
                    RecipeMeasureParser.Parsed parsed = item.unit() == null ? null
                            : new RecipeMeasureParser.Parsed(item.quantity(), item.unit());
                    insertRecipeIngredient(c, id, item.name(), item.sourceMeasure(), parsed);
                }

                // Keep a legacy menu item unpublished; scheduling is the only publish action.
                try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE menu_items SET available_today=0, daily_menu_date=NULL
                    WHERE api_meal_id=? AND NOT EXISTS (
                      SELECT 1 FROM menu_periods mp
                      WHERE mp.menu_item_id=menu_items.menu_item_id AND mp.active=1)
                    """)) {
                    ps.setString(1, meal.id());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private List<ManualIngredient> normalizeMealDbIngredients(List<MealRecipe.Ingredient> ingredients) {
        Map<String, ManualIngredient> unique = new LinkedHashMap<>();
        for (MealRecipe.Ingredient item : ingredients) {
            if (item == null || item.name().isBlank()) { continue; }
            String name = item.name().trim();
            String key = name.toLowerCase(Locale.ROOT);
            RecipeMeasureParser.Parsed parsed = RecipeMeasureParser.parse(item.measure());
            ManualIngredient current = unique.get(key);
            if (current == null) {
                unique.put(key, new ManualIngredient(name,
                        parsed == null ? 0d : parsed.quantity(),
                        parsed == null ? null : parsed.unit(), item.measure()));
                continue;
            }
            if (current.unit() != null && parsed != null) {
                if (current.unit().equalsIgnoreCase(parsed.unit())) {
                    unique.put(key, new ManualIngredient(current.name(), current.quantity() + parsed.quantity(),
                            current.unit(), current.sourceMeasure() + "; " + item.measure()));
                } else if (RecipeMeasureParser.canConvert(parsed.unit(), current.unit())) {
                    double converted = RecipeMeasureParser.convert(parsed.quantity(), parsed.unit(), current.unit());
                    unique.put(key, new ManualIngredient(current.name(), current.quantity() + converted,
                            current.unit(), current.sourceMeasure() + "; " + item.measure()));
                } else {
                    unique.put(key, new ManualIngredient(current.name(), 0d, null,
                            current.sourceMeasure() + "; " + item.measure()));
                }
            } else {
                // Conflicting or vague duplicate measurements must be reviewed, not guessed.
                unique.put(key, new ManualIngredient(current.name(), 0d, null,
                        current.sourceMeasure() + "; " + item.measure()));
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** Manual ingredient format: Ingredient name | quantity | unit (one ingredient per line). */
    public void saveCustom(String name, String category, String instructions, String ingredients) throws SQLException {
        if (name == null || name.isBlank()) { throw new IllegalArgumentException("Recipe name is required."); }
        if (!List.of("APPETIZER", "MAIN_COURSE", "BEVERAGE").contains(category)) {
            throw new IllegalArgumentException("Choose a valid menu category.");
        }
        if (instructions == null || instructions.isBlank()) { throw new IllegalArgumentException("Add preparation instructions."); }
        List<ManualIngredient> parsed = parseManualIngredients(ingredients);
        StringBuilder display = new StringBuilder();
        for (ManualIngredient i : parsed) {
            display.append(i.name()).append(" — ")
                    .append(i.sourceMeasure()).append("\n");
        }

        Database.initialize();
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                long id = save(c, "ADMIN", null, name.trim(), category, "Restaurant Original",
                        instructions.trim(), "", display.toString());
                try (PreparedStatement del = c.prepareStatement("DELETE FROM recipe_ingredients WHERE recipe_id=?")) {
                    del.setLong(1, id); del.executeUpdate();
                }
                for (ManualIngredient i : parsed) {
                    RecipeMeasureParser.Parsed measure = i.quantity() == null || i.unit() == null
                            ? null : new RecipeMeasureParser.Parsed(i.quantity(), i.unit());
                    insertRecipeIngredient(c, id, i.name(), i.sourceMeasure(), measure);
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private record ManualIngredient(String name, Double quantity, String unit, String sourceMeasure) {}

    /**
     * Accepts both "Ingredient | measurement" and the older
     * "Ingredient | quantity | unit" format. Measurements are preserved verbatim;
     * only recognized numeric measures are eligible for automatic stock deduction.
     */
    private List<ManualIngredient> parseManualIngredients(String text) {
        if (text == null || text.isBlank()) { throw new IllegalArgumentException("Add at least one ingredient."); }
        Map<String, ManualIngredient> unique = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) { continue; }
            String[] parts = line.split("\\|", -1);
            if (parts.length < 2 || parts.length > 3 || parts[0].isBlank()) {
                throw new IllegalArgumentException("Invalid ingredient line: '" + line
                        + "'. Use Ingredient | measurement, for example Pork | 1 handful.");
            }
            String ingredientName = parts[0].trim();
            String sourceMeasure;
            if (parts.length == 2) {
                sourceMeasure = parts[1].trim();
            } else {
                String amount = parts[1].trim();
                String unitText = parts[2].trim();
                sourceMeasure = (amount + " " + unitText).trim();
            }
            if (sourceMeasure.isBlank()) { throw new IllegalArgumentException("Enter a measurement or description for " + ingredientName + "."); }
            RecipeMeasureParser.Parsed parsed = RecipeMeasureParser.parse(sourceMeasure);
            ManualIngredient incoming = new ManualIngredient(ingredientName,
                    parsed == null ? null : parsed.quantity(), parsed == null ? null : parsed.unit(), sourceMeasure);
            String key = ingredientName.toLowerCase(Locale.ROOT);
            ManualIngredient existing = unique.get(key);
            if (existing == null) {
                unique.put(key, incoming);
            } else if (existing.quantity() != null && incoming.quantity() != null) {
                if (existing.unit().equalsIgnoreCase(incoming.unit())) {
                    unique.put(key, new ManualIngredient(existing.name(), existing.quantity() + incoming.quantity(),
                            existing.unit(), existing.sourceMeasure() + "; " + incoming.sourceMeasure()));
                } else if (RecipeMeasureParser.canConvert(incoming.unit(), existing.unit())) {
                    double converted = RecipeMeasureParser.convert(incoming.quantity(), incoming.unit(), existing.unit());
                    unique.put(key, new ManualIngredient(existing.name(), existing.quantity() + converted,
                            existing.unit(), existing.sourceMeasure() + "; " + incoming.sourceMeasure()));
                } else {
                    unique.put(key, new ManualIngredient(existing.name(), null, null,
                            existing.sourceMeasure() + "; " + incoming.sourceMeasure()));
                }
            } else {
                unique.put(key, new ManualIngredient(existing.name(), null, null,
                        existing.sourceMeasure() + "; " + incoming.sourceMeasure()));
            }
        }
        if (unique.isEmpty()) { throw new IllegalArgumentException("Add at least one ingredient."); }
        return new ArrayList<>(unique.values());
    }

    private void insertRecipeIngredient(Connection c,long recipeId,String name,String raw,RecipeMeasureParser.Parsed parsed) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("""
            INSERT INTO recipe_ingredients(recipe_id,ingredient_name,source_measure,quantity,unit,parse_status)
            VALUES(?,?,?,?,?,?)
            ON CONFLICT(recipe_id, ingredient_name) DO UPDATE SET
              source_measure=excluded.source_measure, quantity=excluded.quantity,
              unit=excluded.unit, parse_status=excluded.parse_status
            """)) {
            ps.setLong(1,recipeId); ps.setString(2,name.trim()); ps.setString(3,raw==null?"":raw);
            if(parsed==null) { ps.setNull(4,Types.REAL); ps.setNull(5,Types.VARCHAR); }
            else { ps.setDouble(4,parsed.quantity()); ps.setString(5,parsed.unit()); }
            // Both TheMealDB and restaurant-original measurements are accepted as supplied.
            // READY means the recipe can be saved/scheduled, not necessarily auto-deducted.
            ps.setString(6,"READY");
            ps.executeUpdate();
        }
    }

    private long save(Connection c, String source, String externalId, String name, String category,
                      String area, String instructions, String image, String ingredients) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
            INSERT INTO recipe_catalog(source,external_id,name,category,area,instructions,thumbnail_url,ingredients_text)
            VALUES(?,?,?,?,?,?,?,?)
            ON CONFLICT(name) DO UPDATE SET source=excluded.source,external_id=excluded.external_id,
              category=excluded.category,area=excluded.area,instructions=excluded.instructions,
              thumbnail_url=excluded.thumbnail_url,ingredients_text=excluded.ingredients_text
            """)) {
            ps.setString(1, source); ps.setString(2, externalId); ps.setString(3, name);
            ps.setString(4, category); ps.setString(5, area == null ? "" : area);
            ps.setString(6, instructions == null ? "" : instructions);
            ps.setString(7, image == null ? "" : image); ps.setString(8, ingredients == null ? "" : ingredients);
            ps.executeUpdate();
        }
        try (PreparedStatement find = c.prepareStatement("SELECT recipe_id FROM recipe_catalog WHERE name=? COLLATE NOCASE")) {
            find.setString(1, name);
            try (ResultSet rs = find.executeQuery()) {
                if (!rs.next()) { throw new SQLException("Recipe was not saved."); }
                return rs.getLong(1);
            }
        }
    }

    public List<Recipe> findAll() throws SQLException {
        List<Recipe> result=new ArrayList<>();
        try(Connection c=Database.connect();PreparedStatement ps=c.prepareStatement("SELECT recipe_id,source,external_id,name,category,area,instructions,thumbnail_url,ingredients_text FROM recipe_catalog ORDER BY name");ResultSet rs=ps.executeQuery()) {
            while(rs.next()) { result.add(new Recipe(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9))); }
        } return result;
    }
    public Recipe findById(long id) throws SQLException {
        try(Connection c=Database.connect();PreparedStatement ps=c.prepareStatement("SELECT recipe_id,source,external_id,name,category,area,instructions,thumbnail_url,ingredients_text FROM recipe_catalog WHERE recipe_id=?")) {
            ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Recipe no longer exists in the library.");return new Recipe(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9));}
        }
    }
    private static String mapCategory(String category){return "Starter".equalsIgnoreCase(category)?"APPETIZER":"MAIN_COURSE";}
}

