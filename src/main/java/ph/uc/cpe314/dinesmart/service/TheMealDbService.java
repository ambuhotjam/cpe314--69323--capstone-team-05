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
import java.util.Locale;
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


    /** Returns meals listed under a TheMealDB category, such as Main Course or Starter. */
    public List<MealRecipe> searchByCategory(String category) throws IOException, InterruptedException {
        if (category == null || category.isBlank()) throw new IllegalArgumentException("Choose a meal category.");
        String encoded = URLEncoder.encode(category.trim(), StandardCharsets.UTF_8);
        JSONArray meals = getJson("filter.php?c=" + encoded).optJSONArray("meals");
        if (meals == null) return List.of();
        List<MealRecipe> result = new ArrayList<>();
        for (int i = 0; i < meals.length(); i++) {
            JSONObject meal = meals.getJSONObject(i);
            result.add(new MealRecipe(meal.optString("idMeal", ""),
                    meal.optString("strMeal", "Unknown meal"), category, "", "",
                    meal.optString("strMealThumb", ""), List.of()));
        }
        return result;
    }

    public MealRecipe lookupById(String mealId) throws IOException, InterruptedException {
        if (mealId == null || !mealId.matches("\\d+")) throw new IllegalArgumentException("Meal ID must contain digits only.");
        JSONArray meals = getJson("lookup.php?i=" + mealId).optJSONArray("meals");
        if (meals == null || meals.isEmpty()) throw new IOException("No recipe returned for meal ID " + mealId + ".");
        return parseMeal(meals.getJSONObject(0));
    }

    /**
     * Finds a TheMealDB entry only when its normalized dish name exactly matches.
     * This deliberately avoids assigning an unrelated category photo to a dish.
     */
    public MealRecipe findExactByName(String dishName) throws IOException, InterruptedException {
        if (dishName == null || dishName.isBlank()) return null;
        String wanted = normalizeMealName(dishName);
        List<String> searchTerms = new ArrayList<>();
        searchTerms.add(dishName.trim());
        // If the full phrase returns no result, try distinctive words from the name.
        // We still accept a result only when its full normalized name is an exact match.
        for (String token : normalizeMealName(dishName).split("\\s+")) {
            if (token.length() >= 4 && !searchTerms.contains(token)) searchTerms.add(token);
        }
        for (String term : searchTerms) {
            for (MealRecipe candidate : searchByName(term)) {
                if (wanted.equals(normalizeMealName(candidate.name()))
                        && candidate.thumbnailUrl() != null
                        && !candidate.thumbnailUrl().isBlank()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Downloads a TheMealDB-hosted food thumbnail after validating its host and content type. */
    public byte[] downloadThumbnail(String thumbnailUrl) throws IOException, InterruptedException {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            throw new IOException("TheMealDB recipe does not include a photo URL.");
        }
        URI uri;
        try {
            uri = URI.create(thumbnailUrl);
        } catch (IllegalArgumentException ex) {
            throw new IOException("TheMealDB photo URL is invalid.", ex);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !("www.themealdb.com".equals(host) || "themealdb.com".equals(host))
                || uri.getPath() == null || !uri.getPath().startsWith("/images/media/meals/")) {
            throw new IOException("TheMealDB photo URL is not from the trusted recipe image host.");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25))
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg")
                .GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("TheMealDB photo download failed (HTTP " + response.statusCode() + ").");
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        byte[] body = response.body();
        if (!contentType.startsWith("image/") || body == null || body.length == 0 || body.length > 10 * 1024 * 1024) {
            throw new IOException("TheMealDB returned an invalid or oversized photo.");
        }
        return body;
    }

    private static String normalizeMealName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
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
