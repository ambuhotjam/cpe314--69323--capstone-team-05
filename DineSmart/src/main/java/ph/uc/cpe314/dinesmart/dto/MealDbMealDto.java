package ph.uc.cpe314.dinesmart.dto;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** DTO that mirrors TheMealDB's meal JSON fields before domain mapping. */
public record MealDbMealDto(
        String idMeal,
        String strMeal,
        String strCategory,
        String strArea,
        String strInstructions,
        String strMealThumb,
        List<IngredientDto> ingredients) {

    public MealDbMealDto {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
    }

    /** Reads the numbered ingredient and measure fields from one API meal object. */
    public static MealDbMealDto fromJson(JSONObject json) {
        List<IngredientDto> ingredients = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            String name = json.optString("strIngredient" + index, "").trim();
            String measure = json.optString("strMeasure" + index, "").trim();
            if (!name.isBlank()) {
                ingredients.add(new IngredientDto(name, measure));
            }
        }
        return new MealDbMealDto(
                json.optString("idMeal", "").trim(),
                json.optString("strMeal", "").trim(),
                json.optString("strCategory", "").trim(),
                json.optString("strArea", "").trim(),
                json.optString("strInstructions", "").trim(),
                json.optString("strMealThumb", "").trim(),
                ingredients);
    }

    /** API-level ingredient pair; names match the source response shape. */
    public record IngredientDto(String strIngredient, String strMeasure) {
        public IngredientDto {
            strIngredient = strIngredient == null ? "" : strIngredient.trim();
            strMeasure = strMeasure == null ? "" : strMeasure.trim();
        }
    }
}
