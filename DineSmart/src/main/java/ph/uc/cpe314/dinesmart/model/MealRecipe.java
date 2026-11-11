package ph.uc.cpe314.dinesmart.model;

import java.util.List;

/** Recipe data returned by TheMealDB; it does not define restaurant selling prices. */
public record MealRecipe(String id, String name, String category, String area,
                         String instructions, String thumbnailUrl, List<Ingredient> ingredients) {
    public MealRecipe { ingredients = List.copyOf(ingredients); }
    public record Ingredient(String name, String measure) {
        public Ingredient {
            name = name == null ? "" : name.trim();
            measure = measure == null ? "" : measure.trim();
        }
    }
    @Override public String toString() {
        return name + (category == null || category.isBlank() ? "" : " — " + category)
                + (area == null || area.isBlank() ? "" : " (" + area + ")");
    }
}
