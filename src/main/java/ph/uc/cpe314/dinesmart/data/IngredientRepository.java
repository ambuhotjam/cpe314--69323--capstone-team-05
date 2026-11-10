package ph.uc.cpe314.dinesmart.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class IngredientRepository {
    public record Ingredient(String name, double quantity, String unit) {
        @Override public String toString() { return name + "  ·  " + String.format("%.2f", quantity) + " " + unit; }
    }
    public List<Ingredient> findAll() throws Exception {
        List<Ingredient> rows = new ArrayList<>();
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT name,stock_quantity,stock_unit FROM ingredients ORDER BY name"); ResultSet rs=ps.executeQuery()) {
            while(rs.next()) rows.add(new Ingredient(rs.getString("name"),rs.getDouble("stock_quantity"),rs.getString("stock_unit")));
        }
        return rows;
    }
    public List<String> findMenuNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT name FROM menu_items ORDER BY name"); ResultSet rs=ps.executeQuery()) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }
    public List<String> findRequirements(String menuName) throws Exception {
        List<String> rows = new ArrayList<>();
        if (menuName == null || menuName.isBlank()) return rows;
        String sql = "SELECT i.name, mi.quantity_required, mi.measure FROM menu_item_ingredients mi JOIN menu_items m ON m.menu_item_id=mi.menu_item_id JOIN ingredients i ON i.ingredient_id=mi.ingredient_id WHERE m.name=? ORDER BY i.name";
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setString(1, menuName);
            try (ResultSet rs=ps.executeQuery()) {
                while (rs.next()) rows.add(rs.getString(1)+"  ·  "+rs.getDouble(2)+" "+rs.getString(3));
            }
        }
        return rows;
    }
    public void save(String name, double quantity, String unit) throws Exception {
        if(name==null || name.isBlank() || quantity<0 || !Double.isFinite(quantity) || unit==null || unit.isBlank()) throw new IllegalArgumentException("Enter a valid ingredient name, non-negative quantity, and unit.");
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("INSERT INTO ingredients(name,stock_quantity,stock_unit) VALUES(?,?,?) ON CONFLICT(name) DO NOTHING")) {
            ps.setString(1,name.trim()); ps.setDouble(2,quantity); ps.setString(3,unit.trim());
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("That ingredient already exists. Select it from the inventory list and use + Add quantity or − Remove quantity instead.");
        }
    }


    /** Deletes an ingredient only when it is not referenced by a menu recipe or movement history. */
    public void delete(String ingredientName) throws Exception {
        if (ingredientName == null || ingredientName.isBlank())
            throw new IllegalArgumentException("Select an ingredient first.");
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM ingredients WHERE name = ? COLLATE NOCASE")) {
            ps.setString(1, ingredientName.trim());
            if (ps.executeUpdate() == 0)
                throw new IllegalArgumentException("Ingredient was not found. Refresh the inventory and try again.");
        } catch (java.sql.SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("foreign key"))
                throw new IllegalArgumentException("This ingredient is linked to a recipe or stock history. Remove those references before deleting it.", ex);
            throw ex;
        }
    }

    /** Adds or removes physical stock for an existing ingredient without changing its name or unit. */
    public void adjustStock(String ingredientName, double amount, boolean add) throws Exception {
        if (ingredientName == null || ingredientName.isBlank())
            throw new IllegalArgumentException("Select an ingredient first.");
        if (!Double.isFinite(amount) || amount <= 0)
            throw new IllegalArgumentException("Enter an adjustment amount greater than zero.");
        String sql = add
                ? "UPDATE ingredients SET stock_quantity = stock_quantity + ? WHERE name = ? COLLATE NOCASE"
                : "UPDATE ingredients SET stock_quantity = stock_quantity - ? WHERE name = ? COLLATE NOCASE AND stock_quantity >= ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, ingredientName.trim());
            if (!add) ps.setDouble(3, amount);
            int changed = ps.executeUpdate();
            if (changed == 0) {
                if (add) throw new IllegalArgumentException("Ingredient was not found. Refresh the inventory and try again.");
                throw new IllegalArgumentException("Not enough stock to remove " + amount + ". Stock cannot become negative.");
            }
        }
    }

    /** Defines how much of an ingredient one menu serving consumes. */
    public void setRecipeRequirement(String menuName, String ingredientName, double quantity, String measure) throws Exception {
        if(menuName==null || menuName.isBlank() || ingredientName==null || ingredientName.isBlank()
                || !Double.isFinite(quantity) || quantity<=0) throw new IllegalArgumentException("Provide a menu item, ingredient, and positive quantity per serving.");
        String sql = "INSERT INTO menu_item_ingredients(menu_item_id,ingredient_id,quantity_required,measure) " +
                "SELECT m.menu_item_id,i.ingredient_id,?,? FROM menu_items m CROSS JOIN ingredients i WHERE m.name=? AND i.name=? " +
                "ON CONFLICT(menu_item_id,ingredient_id) DO UPDATE SET quantity_required=excluded.quantity_required,measure=excluded.measure";
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setDouble(1,quantity); ps.setString(2,measure==null?"":measure); ps.setString(3,menuName.trim()); ps.setString(4,ingredientName.trim());
            if(ps.executeUpdate()!=1) throw new IllegalArgumentException("Menu item or ingredient was not found. Save both first.");
        }
    }
}
