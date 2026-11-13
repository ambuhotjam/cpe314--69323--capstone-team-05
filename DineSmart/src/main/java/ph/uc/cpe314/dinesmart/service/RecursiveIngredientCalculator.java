package ph.uc.cpe314.dinesmart.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Recursively totals the measurable ingredient requirements for every dish
 * in a customer order. Quantities are already expressed in each ingredient's
 * inventory stock unit by the recipe scheduling workflow.
 */
public final class RecursiveIngredientCalculator {

    /** Ingredient quantity needed for one serving of a menu item. */
    public record IngredientUse(long ingredientId, String ingredientName,
                                String stockUnit, double quantityPerServing) {
        public IngredientUse {
            if (ingredientId <= 0) {
                throw new IllegalArgumentException("Ingredient ID must be positive.");
            }
            if (ingredientName == null || ingredientName.isBlank()) {
                throw new IllegalArgumentException("Ingredient name is required.");
            }
            if (stockUnit == null || stockUnit.isBlank()) {
                throw new IllegalArgumentException("Ingredient stock unit is required.");
            }
            if (!Double.isFinite(quantityPerServing) || quantityPerServing <= 0) {
                throw new IllegalArgumentException("Ingredient quantity per serving must be positive.");
            }
        }
    }

    /** A menu item and the number of servings included in the customer order. */
    public record OrderedMenuItem(String menuItemName, int quantity,
                                  List<IngredientUse> ingredients) {
        public OrderedMenuItem {
            if (menuItemName == null || menuItemName.isBlank()) {
                throw new IllegalArgumentException("Menu item name is required.");
            }
            if (quantity < 1) {
                throw new IllegalArgumentException("Order quantity must be at least one.");
            }
            ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        }
    }

    /** Combined amount required for one ingredient across the whole order. */
    public record IngredientTotal(long ingredientId, String ingredientName,
                                  String stockUnit, double requiredQuantity) { }

    /**
     * Calculates combined ingredient requirements for an order.
     *
     * The base case is reached when every ordered menu item has been processed.
     * Each recursive call processes exactly one more menu item, so the index
     * moves toward that base case and the recursion terminates.
     */
    public List<IngredientTotal> calculate(List<OrderedMenuItem> orderedItems) {
        Objects.requireNonNull(orderedItems, "orderedItems");
        return List.copyOf(calculateFrom(orderedItems, 0, new LinkedHashMap<>()).values());
    }

    private Map<Long, IngredientTotal> calculateFrom(
            List<OrderedMenuItem> orderedItems,
            int index,
            Map<Long, IngredientTotal> totals) {

        // BASE CASE: there are no more order lines to process.
        if (index >= orderedItems.size()) {
            return totals;
        }

        OrderedMenuItem currentItem = orderedItems.get(index);
        for (IngredientUse ingredient : currentItem.ingredients()) {
            double lineRequirement = ingredient.quantityPerServing() * currentItem.quantity();
            if (!Double.isFinite(lineRequirement)) {
                throw new IllegalArgumentException(
                        "Ingredient requirement is too large for " + ingredient.ingredientName() + ".");
            }

            IngredientTotal previous = totals.get(ingredient.ingredientId());
            if (previous == null) {
                totals.put(ingredient.ingredientId(), new IngredientTotal(
                        ingredient.ingredientId(), ingredient.ingredientName(),
                        ingredient.stockUnit(), lineRequirement));
            } else {
                if (!previous.stockUnit().equalsIgnoreCase(ingredient.stockUnit())) {
                    throw new IllegalArgumentException(
                            "Inconsistent inventory units for " + ingredient.ingredientName() + ".");
                }
                double combined = previous.requiredQuantity() + lineRequirement;
                if (!Double.isFinite(combined)) {
                    throw new IllegalArgumentException(
                            "Combined ingredient requirement is too large for " + ingredient.ingredientName() + ".");
                }
                totals.put(ingredient.ingredientId(), new IngredientTotal(
                        previous.ingredientId(), previous.ingredientName(),
                        previous.stockUnit(), combined));
            }
        }

        // RECURSIVE CASE: continue with the next ordered menu item.
        return calculateFrom(orderedItems, index + 1, totals);
    }
}

