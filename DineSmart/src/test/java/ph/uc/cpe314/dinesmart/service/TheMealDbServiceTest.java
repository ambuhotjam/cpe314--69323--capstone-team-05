package ph.uc.cpe314.dinesmart.service;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ph.uc.cpe314.dinesmart.model.MealRecipe;

import static org.junit.jupiter.api.Assertions.*;

/** Uses a local HTTP server; API tests never depend on the public internet. */
class TheMealDbServiceTest {
    private HttpServer server;
    private final List<String> requestedCategories = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesSuccessfulMealResponseAndIngredientMeasures() throws Exception {
        startServer(200, readFixture(), 0);

        List<MealRecipe> meals = service(Duration.ofSeconds(2)).searchByName("arrabiata");

        assertEquals(1, meals.size());
        assertEquals("52771", meals.get(0).id());
        assertEquals("Spicy Arrabiata Penne", meals.get(0).name());
        assertEquals("Italian", meals.get(0).area());
        assertEquals(8, meals.get(0).ingredients().size());
        assertEquals("1 pound", meals.get(0).ingredients().get(0).measure());
    }

    @Test
    void returnsEmptyListWhenTheMealDbReturnsNullMeals() throws Exception {
        startServer(200, "{\"meals\":null}", 0);

        assertTrue(service(Duration.ofSeconds(2)).searchByName("not-a-meal").isEmpty());
    }

    @Test
    void returnsTypedEmptyResultInsteadOfNull() throws Exception {
        startServer(200, "{\"meals\":null}", 0);

        ApiResult<List<MealRecipe>> result =
                service(Duration.ofSeconds(2)).searchByNameResult("not-a-meal");

        assertEquals(ApiResult.Status.EMPTY, result.status());
        assertNotNull(result.data());
        assertTrue(result.data().isEmpty());
        assertTrue(result.isSuccessful());
    }

    @Test
    void returnsTypedFailureResultForApiErrors() throws Exception {
        startServer(503, "{\"message\":\"temporarily unavailable\"}", 0);

        ApiResult<List<MealRecipe>> result =
                service(Duration.ofSeconds(2)).searchByNameResult("pasta");

        assertEquals(ApiResult.Status.FAILED, result.status());
        assertNotNull(result.data());
        assertTrue(result.data().isEmpty());
        assertFalse(result.isSuccessful());
        assertTrue(result.message().contains("HTTP 503"));
    }

    @Test
    void mapsMainCourseToSupportedTheMealDbCategoriesAndDeduplicatesResults()
            throws Exception {
        String beefResponse = "{\"meals\":["
                + "{\"idMeal\":\"52801\",\"strMeal\":\"Beef Stew\","
                + "\"strMealThumb\":\"https://www.themealdb.com/images/media/meals/beef.jpg\"}]}";
        String chickenResponse = "{\"meals\":["
                + "{\"idMeal\":\"52801\",\"strMeal\":\"Beef Stew\","
                + "\"strMealThumb\":\"https://www.themealdb.com/images/media/meals/beef.jpg\"},"
                + "{\"idMeal\":\"52802\",\"strMeal\":\"Chicken Curry\","
                + "\"strMealThumb\":\"https://www.themealdb.com/images/media/meals/chicken.jpg\"}]}";
        startCategoryServer(Map.of("Beef", beefResponse, "Chicken", chickenResponse));

        List<MealRecipe> meals =
                service(Duration.ofSeconds(2)).searchByCategory("Main Course");

        assertEquals(2, meals.size(), "The same meal returned by two categories must appear once.");
        assertEquals("Beef Stew", meals.get(0).name());
        assertEquals("Beef", meals.get(0).category());
        assertEquals("Chicken Curry", meals.get(1).name());
        assertEquals("Chicken", meals.get(1).category());
        assertEquals(10, requestedCategories.size());
        assertTrue(requestedCategories.containsAll(List.of(
                "Beef", "Chicken", "Goat", "Lamb", "Miscellaneous",
                "Pasta", "Pork", "Seafood", "Vegan", "Vegetarian")));
        assertFalse(requestedCategories.contains("Main Course"),
                "DineSmart's label must never be sent as a native TheMealDB category.");
    }

    @Test
    void mapsAppetizerToTheMealDbStarterCategory() throws Exception {
        String starterResponse = "{\"meals\":["
                + "{\"idMeal\":\"52901\",\"strMeal\":\"Bruschetta\","
                + "\"strMealThumb\":\"https://www.themealdb.com/images/media/meals/starter.jpg\"}]}";
        startCategoryServer(Map.of("Starter", starterResponse));

        List<MealRecipe> meals =
                service(Duration.ofSeconds(2)).searchByCategory("Appetizer");

        assertEquals(1, meals.size());
        assertEquals("Bruschetta", meals.get(0).name());
        assertEquals("Starter", meals.get(0).category());
        assertEquals(List.of("Starter"), requestedCategories);
    }

    @Test
    void reportsNonSuccessfulHttpStatus() throws Exception {
        startServer(503, "{\"message\":\"temporarily unavailable\"}", 0);

        IOException error = assertThrows(IOException.class,
                () -> service(Duration.ofSeconds(2)).searchByName("pasta"));
        assertTrue(error.getMessage().contains("HTTP 503"));
    }

    @Test
    void reportsRequestTimeout() throws Exception {
        startServer(200, "{\"meals\":null}", 400);

        assertThrows(IOException.class,
                () -> service(Duration.ofMillis(75)).searchByName("pasta"));
    }

    @Test
    void reportsMalformedJsonInsteadOfReturningNull() throws Exception {
        startServer(200, "{not-valid-json", 0);

        IOException error = assertThrows(IOException.class,
                () -> service(Duration.ofSeconds(2)).searchByName("pasta"));
        assertTrue(error.getMessage().toLowerCase().contains("invalid json"));
    }

    private String readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/themealdb/arrabiata-search.json")) {
            assertNotNull(input, "The saved TheMealDB response fixture must be on the test classpath.");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void startCategoryServer(Map<String, String> responses)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/json/v1/1/filter.php", exchange -> {
            try {
                String rawQuery = exchange.getRequestURI().getRawQuery();
                String encodedCategory = rawQuery != null && rawQuery.startsWith("c=")
                        ? rawQuery.substring(2) : "";
                String category = URLDecoder.decode(
                        encodedCategory, StandardCharsets.UTF_8);
                requestedCategories.add(category);
                String response = responses.getOrDefault(category, "{\"meals\":null}");
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private void startServer(int status, String response, long delayMillis)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/json/v1/1/search.php", exchange -> {
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // A timeout test intentionally closes the client request first.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private TheMealDbService service(Duration timeout) {
        URI endpoint = URI.create("http://127.0.0.1:"
                + server.getAddress().getPort() + "/api/json/v1/1/");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        return new TheMealDbService(client, endpoint, timeout);
    }
}
