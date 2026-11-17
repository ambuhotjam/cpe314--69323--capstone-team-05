package ph.uc.cpe314.dinesmart.service;

import java.util.List;
import ph.uc.cpe314.dinesmart.dto.MealDbMealDto;
import ph.uc.cpe314.dinesmart.model.MealRecipe;

/** Pure mapping from TheMealDB transport fields into the application's recipe model. */
public final class MealDbMealMapper {
    /** Converts one parsed API DTO to a validated domain recipe. */
    public MealRecipe toDomain(MealDbMealDto dto) {
        if (dto == null || dto.idMeal() == null || dto.idMeal().isBlank()) {
            throw new IllegalArgumentException("TheMealDB meal is missing its ID.");
        }
        if (dto.strMeal() == null || dto.strMeal().isBlank()) {
            throw new IllegalArgumentException("TheMealDB meal is missing its name.");
        }

        List<MealRecipe.Ingredient> ingredients = dto.ingredients().stream()
                .filter(ingredient -> !ingredient.strIngredient().isBlank())
                .map(ingredient -> new MealRecipe.Ingredient(
                        ingredient.strIngredient(), ingredient.strMeasure()))
                .toList();

        return new MealRecipe(dto.idMeal(), dto.strMeal(), dto.strCategory(),
                dto.strArea(), dto.strInstructions(), dto.strMealThumb(), ingredients);
    }
}
