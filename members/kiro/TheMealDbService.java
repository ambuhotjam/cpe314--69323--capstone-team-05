package ph.uc.cpe314.dinesmart.service;

import org.json.JSONArray;
import org.json.JSONObject;
import ph.uc.cpe314.dinesmart.model.MealRecipe;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Uses the team's assigned TheMealDB development key 1. */
public final class TheMealDbService {
    private static final String BASE = "https://www.themealdb.com/api/json/v1/1/";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public List<MealRecipe> searchByName(String query) throws IOException, InterruptedException {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Enter a meal name to search.");
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        JSONArray meals = getJson("search.php?s=" + encoded).optJSONArray("meals");
        if (meals == null) return List.of();
        List<MealRecipe> result = new ArrayList<>();
        for (int i = 0; i < meals.length(); i++) result.add(parseMeal(meals.getJSONObject(i)));
        return result;
    }

    public MealRecipe lookupById(String mealId) throws IOException, InterruptedException {
        if (mealId == null || !mealId.matches("\\d+")) throw new IllegalArgumentException("Meal ID must contain digits only.");
        JSONArray meals = getJson("lookup.php?i=" + mealId).optJSONArray("meals");
        if (meals == null || meals.isEmpty()) throw new IOException("No recipe returned for meal ID " + mealId + ".");
        return parseMeal(meals.getJSONObject(0));
    }

    private JSONObject getJson(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("TheMealDB returned HTTP " + response.statusCode() + ".");
        try { return new JSONObject(response.body()); }
        catch (RuntimeException ex) { throw new IOException("TheMealDB returned invalid JSON.", ex); }
    }

    private MealRecipe parseMeal(JSONObject meal) {
        List<MealRecipe.Ingredient> ingredients = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String name = meal.optString("strIngredient" + i, "").trim();
            String measure = meal.optString("strMeasure" + i, "").trim();
            if (!name.isBlank()) ingredients.add(new MealRecipe.Ingredient(name, measure));
        }
        return new MealRecipe(meal.optString("idMeal", ""), meal.optString("strMeal", "Unknown meal"),
                meal.optString("strCategory", ""), meal.optString("strArea", ""),
                meal.optString("strInstructions", ""), meal.optString("strMealThumb", ""), ingredients);
    }
}
