package ph.uc.cpe314.dinesmart.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecursiveIngredientCalculatorTest {
    private final RecursiveIngredientCalculator calculator =
            new RecursiveIngredientCalculator();

    @Test
    void emptyOrderReturnsNoIngredientRequirements() {
        assertTrue(calculator.calculate(List.of()).isEmpty());
    }

    @Test
    void recursivelyCombinesSharedIngredientsAcrossOrderedDishes() {
        var chickenPasta = new RecursiveIngredientCalculator.OrderedMenuItem(
                "Chicken Pasta", 2, List.of(
                        new RecursiveIngredientCalculator.IngredientUse(
                                1, "Chicken", "g", 150),
                        new RecursiveIngredientCalculator.IngredientUse(
                                2, "Garlic", "g", 10)));
        var chickenRice = new RecursiveIngredientCalculator.OrderedMenuItem(
                "Chicken Rice", 1, List.of(
                        new RecursiveIngredientCalculator.IngredientUse(
                                1, "Chicken", "g", 200)));

        List<RecursiveIngredientCalculator.IngredientTotal> totals =
                calculator.calculate(List.of(chickenPasta, chickenRice));

        assertEquals(2, totals.size());
        var chicken = totals.stream()
                .filter(total -> total.ingredientName().equals("Chicken"))
                .findFirst().orElseThrow();
        var garlic = totals.stream()
                .filter(total -> total.ingredientName().equals("Garlic"))
                .findFirst().orElseThrow();
        assertEquals(500d, chicken.requiredQuantity(), 0.0001);
        assertEquals("g", chicken.stockUnit());
        assertEquals(20d, garlic.requiredQuantity(), 0.0001);
    }

    @Test
    void acceptsOrderedItemsWithoutAutomaticIngredientDeductions() {
        var descriptiveOnlyDish = new RecursiveIngredientCalculator.OrderedMenuItem(
                "Herb Salad", 3, List.of());

        assertTrue(calculator.calculate(List.of(descriptiveOnlyDish)).isEmpty());
    }

    @Test
    void rejectsInconsistentUnitsForTheSameIngredient() {
        var grams = new RecursiveIngredientCalculator.OrderedMenuItem(
                "Dish A", 1, List.of(new RecursiveIngredientCalculator.IngredientUse(
                        1, "Flour", "g", 100)));
        var kilograms = new RecursiveIngredientCalculator.OrderedMenuItem(
                "Dish B", 1, List.of(new RecursiveIngredientCalculator.IngredientUse(
                        1, "Flour", "kg", 0.2)));

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(List.of(grams, kilograms)));
    }

    @Test
    void rejectsInvalidOrderQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecursiveIngredientCalculator.OrderedMenuItem(
                        "Invalid Dish", 0, List.of()));
    }
}
