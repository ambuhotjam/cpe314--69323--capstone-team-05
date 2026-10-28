package ph.uc.cpe314.dinesmart.data;

import ph.uc.cpe314.dinesmart.model.MealRecipe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Stores imported recipe metadata and ingredient links in SQLite transactionally. */
public final class MealRecipeRepository {
    public void importRecipe(MealRecipe recipe) throws SQLException {
        if (recipe == null || recipe.id().isBlank() || recipe.name().isBlank())
            throw new IllegalArgumentException("Recipe ID and name are required.");
        Database.initialize();
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement upsert = c.prepareStatement("""
                    INSERT INTO menu_items(name, item_type, price, base_prep_minutes, api_meal_id)
                    VALUES (?, 'MAIN_COURSE', 0, 0, ?)
                    ON CONFLICT(name) DO UPDATE SET api_meal_id=excluded.api_meal_id
                    """)) {
                    upsert.setString(1, recipe.name()); upsert.setString(2, recipe.id()); upsert.executeUpdate();
                }
                long menuItemId;
                try (PreparedStatement find = c.prepareStatement("SELECT menu_item_id FROM menu_items WHERE name=?")) {
                    find.setString(1, recipe.name());
                    try (ResultSet rs = find.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Imported menu item was not found after saving.");
                        menuItemId = rs.getLong(1);
                    }
                }
                for (MealRecipe.Ingredient ingredient : recipe.ingredients()) {
                    if (ingredient.name().isBlank()) continue;
                    try (PreparedStatement upsert = c.prepareStatement(
                            "INSERT INTO ingredients(name, stock_quantity, stock_unit) VALUES (?, 0, 'unit') ON CONFLICT(name) DO NOTHING")) {
                        upsert.setString(1, ingredient.name()); upsert.executeUpdate();
                    }
                    long ingredientId;
                    try (PreparedStatement find = c.prepareStatement("SELECT ingredient_id FROM ingredients WHERE name=?")) {
                        find.setString(1, ingredient.name());
                        try (ResultSet rs = find.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Ingredient not found: " + ingredient.name());
                            ingredientId = rs.getLong(1);
                        }
                    }
                    try (PreparedStatement link = c.prepareStatement("""
                        INSERT INTO menu_item_ingredients(menu_item_id, ingredient_id, quantity_required, measure)
                        VALUES (?, ?, 1, ?)
                        ON CONFLICT(menu_item_id, ingredient_id) DO UPDATE SET measure=excluded.measure
                        """)) {
                        link.setLong(1, menuItemId); link.setLong(2, ingredientId); link.setString(3, ingredient.measure()); link.executeUpdate();
                    }
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                if (ex instanceof SQLException sql) throw sql;
                if (ex instanceof RuntimeException runtime) throw runtime;
                throw new SQLException("Could not import recipe.", ex);
            } finally { c.setAutoCommit(true); }
        }
    }
}
