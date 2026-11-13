package ph.uc.cpe314.dinesmart.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ph.uc.cpe314.dinesmart.dto.MealDbMealDto;
import ph.uc.cpe314.dinesmart.model.MealRecipe;

/** HTTP client for TheMealDB; it returns domain recipes rather than raw JSON. */
public final class TheMealDbService {
    private static final URI DEFAULT_BASE =
            URI.create("https://www.themealdb.com/api/json/v1/1/");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration IMAGE_TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    /*
     * TheMealDB does not have a literal "Main Course" category. DineSmart's
     * Main Course browser therefore combines the API's actual savoury dish
     * categories. Starter is kept separate for DineSmart appetizers.
     */
    private static final List<String> MAIN_COURSE_API_CATEGORIES = List.of(
            "Beef", "Chicken", "Goat", "Lamb", "Miscellaneous",
            "Pasta", "Pork", "Seafood", "Vegan", "Vegetarian");

    private final HttpClient client;
    private final URI baseUri;
    private final Duration requestTimeout;
    private final MealDbMealMapper mapper = new MealDbMealMapper();

    public TheMealDbService() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                DEFAULT_BASE, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Creates a client with an injectable endpoint and timeout for deterministic tests.
     *
     * @param client HTTP client
     * @param baseUri API base URI ending in a slash
     * @param requestTimeout maximum time for each API request
     */
    public TheMealDbService(HttpClient client, URI baseUri, Duration requestTimeout) {
        if (client == null || baseUri == null || requestTimeout == null
                || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("HTTP client, base URI, and positive timeout are required.");
        }
        this.client = client;
        this.baseUri = baseUri.toString().endsWith("/")
                ? baseUri : URI.create(baseUri + "/");
        this.requestTimeout = requestTimeout;
    }

    /** Searches TheMealDB by meal name. */
    public List<MealRecipe> searchByName(String query)
            throws IOException, InterruptedException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Enter a meal name to search.");
        }
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        JSONArray meals = getJson("search.php?s=" + encoded).optJSONArray("meals");
        if (meals == null) {
            return List.of();
        }
        List<MealRecipe> result = new ArrayList<>();
        for (int index = 0; index < meals.length(); index++) {
            result.add(mapper.toDomain(MealDbMealDto.fromJson(meals.getJSONObject(index))));
        }
        return List.copyOf(result);
    }

    /**
     * Searches by name and returns a typed result so callers can display
     * OK, EMPTY, or FAILED without interpreting exceptions or null values.
     */
    public ApiResult<List<MealRecipe>> searchByNameResult(String query) {
        try {
            List<MealRecipe> meals = searchByName(query);
            if (meals.isEmpty()) {
                return new ApiResult<>(ApiResult.Status.EMPTY, meals,
                        "No recipes matched your search.");
            }
            return new ApiResult<>(ApiResult.Status.OK, meals,
                    "Recipes loaded successfully.");
        } catch (IOException ex) {
            return new ApiResult<>(ApiResult.Status.FAILED, List.of(),
                    ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new ApiResult<>(ApiResult.Status.FAILED, List.of(),
                    "Recipe search was interrupted.");
        } catch (IllegalArgumentException ex) {
            return new ApiResult<>(ApiResult.Status.FAILED, List.of(),
                    ex.getMessage());
        }
    }

    /**
     * Browses a DineSmart category or a native TheMealDB category.
     *
     * DineSmart's "Main Course" is not a native TheMealDB category, so it
     * aggregates the supported savoury categories and removes duplicate meals.
     * DineSmart's "Appetizer" maps to TheMealDB's native "Starter" category.
     * Any other category is passed through as a native TheMealDB category.
     */
    public List<MealRecipe> searchByCategory(String category)
            throws IOException, InterruptedException {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Choose a meal category.");
        }

        List<String> apiCategories = resolveApiCategories(category);
        Map<String, MealRecipe> uniqueMeals = new LinkedHashMap<>();

        for (String apiCategory : apiCategories) {
            String encoded = URLEncoder.encode(apiCategory, StandardCharsets.UTF_8);
            JSONArray meals = getJson("filter.php?c=" + encoded).optJSONArray("meals");
            if (meals == null) {
                continue;
            }

            for (int index = 0; index < meals.length(); index++) {
                MealDbMealDto dto = MealDbMealDto.fromJson(meals.getJSONObject(index));
                MealRecipe recipe = mapper.toDomain(dto);
                String recipeCategory = recipe.category() == null
                        || recipe.category().isBlank()
                        ? apiCategory : recipe.category();

                MealRecipe categorized = new MealRecipe(recipe.id(), recipe.name(),
                        recipeCategory, recipe.area(), recipe.instructions(),
                        recipe.thumbnailUrl(), recipe.ingredients());
                uniqueMeals.putIfAbsent(categorized.id(), categorized);
            }
        }

        return List.copyOf(uniqueMeals.values());
    }

    private static List<String> resolveApiCategories(String category) {
        String normalized = category.trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "main course", "main_course", "main-course", "maincourse" ->
                    MAIN_COURSE_API_CATEGORIES;
            case "appetizer", "appetizers", "appetizer (starter)", "starter" ->
                    List.of("Starter");
            default -> List.of(category.trim());
        };
    }

    /** Loads one full recipe by its numeric TheMealDB ID. */
    public MealRecipe lookupById(String mealId)
            throws IOException, InterruptedException {
        if (mealId == null || !mealId.matches("\\d+")) {
            throw new IllegalArgumentException("Meal ID must contain digits only.");
        }
        JSONArray meals = getJson("lookup.php?i=" + mealId).optJSONArray("meals");
        if (meals == null || meals.isEmpty()) {
            throw new IOException("No recipe returned for meal ID " + mealId + ".");
        }
        return mapper.toDomain(MealDbMealDto.fromJson(meals.getJSONObject(0)));
    }

    /**
     * Finds a TheMealDB entry only when its normalized dish name exactly matches.
     * This avoids assigning an unrelated category photo to a dish.
     */
    public MealRecipe findExactByName(String dishName)
            throws IOException, InterruptedException {
        if (dishName == null || dishName.isBlank()) {
            return null;
        }
        String wanted = normalizeMealName(dishName);
        List<String> searchTerms = new ArrayList<>();
        searchTerms.add(dishName.trim());
        for (String token : wanted.split("\\s+")) {
            if (token.length() >= 4 && !searchTerms.contains(token)) {
                searchTerms.add(token);
            }
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

    /** Downloads a trusted TheMealDB thumbnail after validating host and content type. */
    public byte[] downloadThumbnail(String thumbnailUrl)
            throws IOException, InterruptedException {
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
                || uri.getPath() == null
                || !uri.getPath().startsWith("/images/media/meals/")) {
            throw new IOException("TheMealDB photo URL is not from the trusted recipe image host.");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(IMAGE_TIMEOUT)
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg")
                .GET()
                .build();
        HttpResponse<byte[]> response =
                client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("TheMealDB photo download failed (HTTP "
                    + response.statusCode() + ").");
        }
        String contentType = response.headers().firstValue("Content-Type")
                .orElse("").toLowerCase(Locale.ROOT);
        byte[] body = response.body();
        if (!contentType.startsWith("image/") || body == null
                || body.length == 0 || body.length > MAX_IMAGE_BYTES) {
            throw new IOException("TheMealDB returned an invalid or oversized photo.");
        }
        return body;
    }

    private static String normalizeMealName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private JSONObject getJson(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("TheMealDB returned HTTP " + response.statusCode() + ".");
        }
        try {
            return new JSONObject(response.body());
        } catch (JSONException ex) {
            throw new IOException("TheMealDB returned invalid JSON.", ex);
        }
    }
}
