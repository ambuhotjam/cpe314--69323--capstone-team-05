package ph.uc.cpe314.dinesmart.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ph.uc.cpe314.dinesmart.model.MealRecipe;
import ph.uc.cpe314.dinesmart.model.MainCourse;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Integration tests use a fresh temporary SQLite file; the production dinesmart.db is never opened. */
class RepositoryIntegrationTest {
    @TempDir Path tempDir;
    private String previousUrl;

    @BeforeEach void setUp() throws Exception {
        previousUrl = System.getProperty("dinesmart.db.url");
        System.setProperty("dinesmart.db.url", "jdbc:sqlite:" + tempDir.resolve("test-dinesmart.db"));
        Database.initialize();
    }
    @AfterEach void tearDown() {
        if (previousUrl == null) System.clearProperty("dinesmart.db.url");
        else System.setProperty("dinesmart.db.url", previousUrl);
    }

    private long addMenu(String name) throws Exception {
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(
                "INSERT INTO menu_items(name,item_type,price,price_cents,base_prep_minutes,available_today,daily_menu_date) VALUES(?,'MAIN_COURSE',250,25000,15,0,NULL)")) {
            ps.setString(1,name); ps.executeUpdate();
        }
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT menu_item_id FROM menu_items WHERE name=?")) {
            ps.setString(1,name); try(ResultSet rs=ps.executeQuery()){assertTrue(rs.next());return rs.getLong(1);}
        }
    }

    private void addTimedOrder(String orderId, String menuName, int ageMinutes, String status) throws Exception {
        long menuId = addMenu(menuName);
        try (Connection c = Database.connect(); PreparedStatement prep = c.prepareStatement("UPDATE menu_items SET base_prep_minutes=20 WHERE menu_item_id=?")) {
            prep.setLong(1, menuId); prep.executeUpdate();
        }
        try (Connection c = Database.connect();
             PreparedStatement order = c.prepareStatement("INSERT INTO restaurant_orders(order_id,ordered_at,status,customer_name) VALUES(?,datetime('now','localtime',?),?,?)")) {
            order.setString(1, orderId);
            order.setString(2, "-" + ageMinutes + " minutes");
            order.setString(3, status);
            order.setString(4, "Test Customer");
            order.executeUpdate();
        }
        try (Connection c = Database.connect();
             PreparedStatement item = c.prepareStatement("INSERT INTO order_items(order_id,menu_item_id,quantity,unit_price_at_order,unit_price_cents) VALUES(?,?,1,250,25000)")) {
            item.setString(1, orderId);
            item.setLong(2, menuId);
            item.executeUpdate();
        }
    }

    @Test void savedMenuItemPhotoPathIsAvailableForTheMenuGallery() throws Exception {
        MenuItemRepository repo = new MenuItemRepository();
        Path photoFile = tempDir.resolve("grilled-salmon.jpg");
        Files.write(photoFile, new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
        String photoPath = photoFile.toString();
        repo.insert(new MainCourse("Grilled Salmon Photo Test", 42900L, 20), photoPath);

        // A new repository instance models reopening the saved menu gallery.
        var saved = new MenuItemRepository().findMenuCards().stream()
                .filter(item -> item.name().equals("Grilled Salmon Photo Test"))
                .findFirst().orElseThrow();
        assertEquals(photoPath, saved.imagePath());
        assertTrue(Files.isRegularFile(Path.of(saved.imagePath())));
        assertEquals(42900L, saved.priceCents());

        // Updating an existing dish without uploading a replacement keeps its photo.
        repo.insert(new MainCourse("Grilled Salmon Photo Test", 44900L, 22), null);
        var updated = new MenuItemRepository().findMenuCards().stream()
                .filter(item -> item.name().equals("Grilled Salmon Photo Test"))
                .findFirst().orElseThrow();
        assertEquals(photoPath, updated.imagePath());
        assertEquals(44900L, updated.priceCents());
    }

    @Test void exactMealDbPhotoCanBeBackfilledAndPersistsWithMealId() throws Exception {
        MenuItemRepository repo = new MenuItemRepository();
        long id = repo.insert(new MainCourse("Spicy Arrabiata Penne", 65000L, 15), null, null);
        String localPhoto = tempDir.resolve("spicy-arrabiata-penne-themealdb-52771.jpg").toString();
        assertTrue(repo.updateMealDbPhoto(id, localPhoto, "52771", null));

        var saved = repo.findMenuCards().stream()
                .filter(item -> item.name().equals("Spicy Arrabiata Penne"))
                .findFirst().orElseThrow();
        assertEquals(localPhoto, saved.imagePath());
        assertEquals("52771", saved.apiMealId());

        // An automatic lookup cannot overwrite a photo changed by a staff member meanwhile.
        assertFalse(repo.updateMealDbPhoto(id, tempDir.resolve("other.jpg").toString(), "52771",
                "an-outdated-photo-path.jpg"));
    }

    @Test void preparationWarningAppearsFiveMinutesBeforeEstimatedDeadline() throws Exception {
        addTimedOrder("ALMOST-DUE", "Almost Due Dish", 15, "PENDING"); // 20-minute estimate, 15 elapsed.
        addTimedOrder("NOT-YET", "Not Yet Dish", 10, "PENDING");
        var alerts = new OrderRepository().findOrdersApproachingPreparationDeadline(5);
        assertTrue(alerts.stream().anyMatch(row -> row.startsWith("ALMOST-DUE")));
        assertFalse(alerts.stream().anyMatch(row -> row.startsWith("NOT-YET")));
    }

    @Test void preparationDeadlineAndUrgentDelayAlertsExcludeCompletedOrders() throws Exception {
        addTimedOrder("DUE-NOW", "Due Now Dish", 21, "PENDING");
        addTimedOrder("URGENT", "Urgent Dish", 31, "PREPARING");
        addTimedOrder("DONE", "Completed Dish", 40, "COMPLETED");
        OrderRepository repo = new OrderRepository();
        assertTrue(repo.findDelayedOrders(0).stream().anyMatch(row -> row.startsWith("DUE-NOW")));
        assertTrue(repo.findDelayedOrders(10).stream().anyMatch(row -> row.startsWith("URGENT")));
        assertFalse(repo.findDelayedOrders(0).stream().anyMatch(row -> row.startsWith("DONE")));
    }

    @Test void dailyMenuCanBeAddedAndRemovedWithoutDeletingSavedDish() throws Exception {
        MenuItemRepository repo=new MenuItemRepository(); addMenu("Test Adobo");
        repo.setDailyAvailabilityByName("Test Adobo",true);
        assertTrue(repo.findAvailableSummaries().stream().anyMatch(s->s.contains("Test Adobo")));
        repo.setDailyAvailabilityByName("Test Adobo",false);
        assertFalse(repo.findAvailableSummaries().stream().anyMatch(s->s.contains("Test Adobo")));
        assertTrue(repo.findAllSummaries().stream().anyMatch(s->s.contains("Test Adobo")));
    }

    @Test void soldOutDishIsNotOrderableEvenWhenOnDailyMenu() throws Exception {
        MenuItemRepository repo=new MenuItemRepository(); addMenu("Sold Out Curry");
        repo.setDailyAvailabilityByName("Sold Out Curry",true);
        repo.toggleSoldOutByName("Sold Out Curry");
        assertFalse(repo.findAvailableSummaries().stream().anyMatch(s->s.contains("Sold Out Curry")));
        repo.toggleSoldOutByName("Sold Out Curry");
        assertTrue(repo.findAvailableSummaries().stream().anyMatch(s->s.contains("Sold Out Curry")));
    }

    @Test void unusedDishIsPermanentlyDeleted() throws Exception {
        MenuItemRepository repo=new MenuItemRepository(); addMenu("Unused Soup");
        assertTrue(repo.deleteByName("Unused Soup"));
        assertFalse(repo.findAllSummaries().stream().anyMatch(s->s.contains("Unused Soup")));
        assertFalse(repo.deleteByName("Unused Soup"));
    }

    @Test void dishWithOrderHistoryIsArchivedAndHistoryIsPreserved() throws Exception {
        MenuItemRepository repo=new MenuItemRepository(); long menuId=addMenu("Ordered Pasta");
        try(Connection c=Database.connect(); PreparedStatement order=c.prepareStatement("INSERT INTO restaurant_orders(order_id,ordered_at,status) VALUES('TEST-ORDER','2026-01-01 12:00','COMPLETED')")) { order.executeUpdate(); }
        try(Connection c=Database.connect(); PreparedStatement item=c.prepareStatement("INSERT INTO order_items(order_id,menu_item_id,quantity,unit_price_at_order,unit_price_cents) VALUES('TEST-ORDER',?,1,250,25000)")) { item.setLong(1,menuId); item.executeUpdate(); }
        assertTrue(repo.deleteByName("Ordered Pasta"));
        assertFalse(repo.findAllSummaries().stream().anyMatch(s->s.contains("Ordered Pasta")));
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM order_items WHERE order_id='TEST-ORDER'" ); ResultSet rs=ps.executeQuery()) { assertTrue(rs.next()); assertEquals(1,rs.getInt(1)); }
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT is_archived FROM menu_items WHERE menu_item_id=?")) { ps.setLong(1,menuId); try(ResultSet rs=ps.executeQuery()){assertTrue(rs.next());assertEquals(1,rs.getInt(1));} }
    }

    @Test void manualRecipeStoresIngredientsAndQuantities() throws Exception {
        RecipeCatalogRepository repo=new RecipeCatalogRepository();
        repo.saveCustom("Test Manual Bowl","MAIN_COURSE","Mix and serve","Chicken | 150 | g\nSoy sauce | 20 | ml");
        var recipe=repo.findAll().stream().filter(r->r.name().equals("Test Manual Bowl")).findFirst().orElseThrow();
        var ingredients=repo.findIngredients(recipe.id());
        assertEquals(2,ingredients.size());
        assertTrue(ingredients.stream().anyMatch(i->i.name().equals("Chicken") && i.quantity()==150d && i.unit().equals("g") && i.status().equals("READY")));
    }

    @Test void missingRecipeIngredientsCanBeAddedToInventoryWithoutChangingPhysicalStock() throws Exception {
        RecipeCatalogRepository catalog = new RecipeCatalogRepository();
        catalog.saveCustom("Inventory Match Test", "MAIN_COURSE", "Cook", "Chicken | 150 | g\nGarlic | 10 | g");
        long recipeId = catalog.findAll().stream().filter(r -> r.name().equals("Inventory Match Test")).findFirst().orElseThrow().id();
        IngredientRepository inventory = new IngredientRepository();
        inventory.save("Chicken", 2500, "g");
        assertEquals(1, catalog.addMissingInventoryIngredients(recipeId));
        assertEquals(0, catalog.addMissingInventoryIngredients(recipeId));
        try (Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT name,stock_quantity FROM ingredients ORDER BY name"); ResultSet rs=ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("Chicken", rs.getString(1));
            assertEquals(2500d, rs.getDouble(2), 0.0001);
            assertTrue(rs.next());
            assertEquals("Garlic", rs.getString(1));
            assertEquals(0d, rs.getDouble(2), 0.0001);
        }
    }

    @Test void missingIngredientActionAddsDescriptiveMeasurementsWithZeroStock() throws Exception {
        RecipeCatalogRepository catalog = new RecipeCatalogRepository();
        catalog.saveMealDb(new MealRecipe("99881", "Review Button Test", "Main", "Test", "Cook", "", List.of(
                new MealRecipe.Ingredient("Soy Sauce", "1 tbs"),
                new MealRecipe.Ingredient("Pork", "1 chopped"))));
        long recipeId = catalog.findAll().stream().filter(r -> r.name().equals("Review Button Test")).findFirst().orElseThrow().id();

        var result = catalog.addMissingInventoryIngredientsDetailed(recipeId);
        assertEquals(2, result.added());
        assertEquals(0, result.needsReview());
        assertTrue(result.reviewNames().isEmpty());

        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT name,stock_quantity,stock_unit FROM ingredients WHERE name='Soy Sauce' COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0d, rs.getDouble("stock_quantity"), 0.0001);
            assertEquals("tbsp", rs.getString("stock_unit"));
        }
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT stock_quantity,stock_unit FROM ingredients WHERE name='Pork' COLLATE NOCASE");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0d, rs.getDouble("stock_quantity"), 0.0001);
            assertEquals("unit", rs.getString("stock_unit"));
        }
        assertEquals(0, catalog.addMissingInventoryIngredientsDetailed(recipeId).added());
    }

    @Test void mealDbImportPreservesMeasurementsAndAcceptsVagueOnes() throws Exception {
        RecipeCatalogRepository repo=new RecipeCatalogRepository();
        MealRecipe meal=new MealRecipe("12345","Test Meal","Chicken","Filipino","Cook thoroughly","",List.of(
                new MealRecipe.Ingredient("Chicken","150 g"), new MealRecipe.Ingredient("Salt","to taste")));
        repo.saveMealDb(meal);
        var recipe=repo.findAll().stream().filter(r->r.name().equals("Test Meal")).findFirst().orElseThrow();
        var rows=repo.findIngredients(recipe.id());
        assertEquals(2,rows.size());
        assertTrue(rows.stream().anyMatch(i->i.name().equals("Chicken") && i.quantity()==150d && "READY".equals(i.status())));
        assertTrue(rows.stream().anyMatch(i->i.name().equals("Salt") && i.quantity()==null && "READY".equals(i.status()) && "to taste".equals(i.sourceMeasure())));
    }

    @Test void mealDbImportMergesDuplicateIngredientNamesAndCanBeSavedRepeatedly() throws Exception {
        RecipeCatalogRepository repo = new RecipeCatalogRepository();
        MealRecipe first = new MealRecipe("DUP-001", "Duplicate Ingredient Test", "Main", "Test", "Cook", "", List.of(
                new MealRecipe.Ingredient("Chicken", "100 g"),
                new MealRecipe.Ingredient(" chicken ", "50 g"),
                new MealRecipe.Ingredient("Soy Sauce", "1 tbsp")));

        assertDoesNotThrow(() -> repo.saveMealDb(first));
        var saved = repo.findAll().stream()
                .filter(r -> r.name().equalsIgnoreCase("Duplicate Ingredient Test"))
                .findFirst().orElseThrow();
        var ingredients = repo.findIngredients(saved.id());
        assertEquals(2, ingredients.size(), "Same ingredient names should be stored once per recipe.");
        var chicken = ingredients.stream().filter(i -> i.name().equalsIgnoreCase("Chicken")).findFirst().orElseThrow();
        assertEquals(150d, chicken.quantity(), 0.0001, "Duplicate quantities in the same unit should be combined.");
        assertEquals("g", chicken.unit());

        MealRecipe updated = new MealRecipe("DUP-001", "Duplicate Ingredient Test", "Main", "Test", "Updated instructions", "", List.of(
                new MealRecipe.Ingredient("Chicken", "200 g"),
                new MealRecipe.Ingredient("Rice", "300 g")));
        assertDoesNotThrow(() -> repo.saveMealDb(updated), "Saving the same recipe again should update, not fail.");
        var updatedRows = repo.findIngredients(saved.id());
        assertEquals(2, updatedRows.size());
        assertTrue(updatedRows.stream().anyMatch(i -> i.name().equalsIgnoreCase("Chicken") && i.quantity() == 200d));
        assertTrue(updatedRows.stream().anyMatch(i -> i.name().equalsIgnoreCase("Rice") && i.quantity() == 300d));
    }

    @Test void duplicateMealDbIngredientWithIncompatibleUnitsIsPreservedWithoutBlockingSave() throws Exception {
        RecipeCatalogRepository repo = new RecipeCatalogRepository();
        repo.saveMealDb(new MealRecipe("DUP-002", "Conflicting Units Test", "Main", "Test", "Cook", "", List.of(
                new MealRecipe.Ingredient("Garlic", "100 g"),
                new MealRecipe.Ingredient("garlic", "2 cloves"))));
        var recipe = repo.findAll().stream().filter(r -> r.name().equals("Conflicting Units Test")).findFirst().orElseThrow();
        var rows = repo.findIngredients(recipe.id());
        assertEquals(1, rows.size());
        assertEquals("READY", rows.get(0).status());
        assertNull(rows.get(0).quantity());
        assertTrue(rows.get(0).sourceMeasure().contains("100 g"));
        assertTrue(rows.get(0).sourceMeasure().contains("2 cloves"));
    }

    @Test void manualRecipeDuplicateIngredientsAreCombined() throws Exception {
        RecipeCatalogRepository repo = new RecipeCatalogRepository();
        repo.saveCustom("Manual Duplicate Test", "MAIN_COURSE", "Cook",
                "Chicken | 100 | g\n chicken | 50 | g");
        var recipe = repo.findAll().stream().filter(r -> r.name().equals("Manual Duplicate Test")).findFirst().orElseThrow();
        var rows = repo.findIngredients(recipe.id());
        assertEquals(1, rows.size());
        assertEquals(150d, rows.get(0).quantity(), 0.0001);
    }

    @Test void manualRestaurantRecipeAcceptsDescriptiveMeasurements() throws Exception {
        RecipeCatalogRepository repo = new RecipeCatalogRepository();
        repo.saveCustom("Restaurant Descriptive Test", "MAIN_COURSE", "Prepare to taste",
                "Coriander | 1 handful\nLime | Juice of 3 limes\nSalt | to taste");
        var recipe = repo.findAll().stream().filter(r -> r.name().equals("Restaurant Descriptive Test")).findFirst().orElseThrow();
        var rows = repo.findIngredients(recipe.id());
        assertEquals(3, rows.size());
        assertTrue(rows.stream().allMatch(r -> "READY".equals(r.status())));
        assertTrue(rows.stream().anyMatch(r -> r.name().equals("Coriander") && "1 handful".equals(r.sourceMeasure())));
        assertTrue(rows.stream().anyMatch(r -> r.name().equals("Lime") && "Juice of 3 limes".equals(r.sourceMeasure())));
        assertTrue(rows.stream().anyMatch(r -> r.name().equals("Salt") && "to taste".equals(r.sourceMeasure())));
    }

    @Test void schedulingRecipeCreatesMenuAndLinksConvertedIngredientRequirements() throws Exception {
        RecipeCatalogRepository catalog=new RecipeCatalogRepository();
        catalog.saveCustom("Scheduled Chicken","MAIN_COURSE","Cook","Chicken | 0.5 | kg");
        long recipeId=catalog.findAll().get(0).id();
        new IngredientRepository().save("Chicken",5000,"g");
        new MenuPeriodRepository().scheduleRecipe(recipeId,399,20,"DINNER",LocalDate.now().toString(),LocalDate.now().plusDays(2).toString());
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT mi.quantity_required,mi.measure FROM menu_item_ingredients mi JOIN menu_items m ON m.menu_item_id=mi.menu_item_id WHERE m.name='Scheduled Chicken'" ); ResultSet rs=ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(500d,rs.getDouble(1),0.0001); assertEquals("g",rs.getString(2));
        }
        assertEquals(1,new MenuPeriodRepository().findAll().size());
    }

    @Test void incompatibleInventoryUnitDoesNotBlockSchedulingAndIsNotDeducted() throws Exception {
        RecipeCatalogRepository catalog=new RecipeCatalogRepository();
        catalog.saveCustom("Unit Mismatch Dish","MAIN_COURSE","Cook","Oil | 20 ml");
        long recipeId=catalog.findAll().get(0).id();
        new IngredientRepository().save("Oil",2,"pcs");
        assertDoesNotThrow(()->new MenuPeriodRepository().scheduleRecipe(
                recipeId,150,10,"DINNER",LocalDate.now().toString(),LocalDate.now().plusDays(1).toString()));
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM menu_items WHERE name='Unit Mismatch Dish'"); ResultSet rs=ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(1,rs.getInt(1));
        }
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM menu_item_ingredients mi JOIN menu_items m ON m.menu_item_id=mi.menu_item_id WHERE m.name='Unit Mismatch Dish'"); ResultSet rs=ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(0,rs.getInt(1),"An incompatible unit must not create an unsafe deduction mapping.");
        }
    }

    @Test void schedulingRejectsInvalidPriceAndDateRange() throws Exception {
        RecipeCatalogRepository catalog=new RecipeCatalogRepository(); catalog.saveCustom("Date Test","MAIN_COURSE","Cook","Rice | 100 | g");
        long id=catalog.findAll().get(0).id(); MenuPeriodRepository periods=new MenuPeriodRepository();
        assertThrows(IllegalArgumentException.class,()->periods.scheduleRecipe(id,0,10,"DINNER",LocalDate.now().toString(),LocalDate.now().plusDays(1).toString()));
        assertThrows(IllegalArgumentException.class,()->periods.scheduleRecipe(id,100,10,"DINNER",LocalDate.now().plusDays(2).toString(),LocalDate.now().toString()));
    }

    @Test void ingredientRequirementMustReferenceExistingMenuAndIngredient() throws Exception {
        addMenu("Ingredient Test Dish"); IngredientRepository ingredients=new IngredientRepository(); ingredients.save("Rice",1000,"g");
        ingredients.setRecipeRequirement("Ingredient Test Dish","Rice",100,"g");
        assertTrue(ingredients.findRequirements("Ingredient Test Dish").stream().anyMatch(s->s.contains("100.0 g")));
        assertThrows(IllegalArgumentException.class,()->ingredients.setRecipeRequirement("Missing Dish","Rice",100,"g"));
        assertThrows(IllegalArgumentException.class,()->ingredients.setRecipeRequirement("Ingredient Test Dish","Rice",0,"g"));
    }

    @Test void scheduledRecipeAppearsInOrderingAndDeductsInventory() throws Exception {
        RecipeCatalogRepository catalog=new RecipeCatalogRepository();
        catalog.saveCustom("Scheduled Adobo","MAIN_COURSE","Cook","Chicken | 250 | g");
        long recipeId=catalog.findAll().get(0).id();
        new IngredientRepository().save("Chicken",2000,"g");
        new MenuPeriodRepository().scheduleRecipe(recipeId,320,25,"DINNER",LocalDate.now().toString(),LocalDate.now().plusDays(1).toString());

        OrderRepository orders=new OrderRepository();
        assertTrue(orders.findOrderableItems().stream().anyMatch(i->i.name().equals("Scheduled Adobo")));
        String orderId=orders.placeOrder("Integration Test",java.util.Map.of("Scheduled Adobo",2));
        assertNotNull(orderId);
        assertTrue(orders.findActiveQueue().stream().anyMatch(row -> row.startsWith(orderId + "  |")),
                "A newly placed pending order must appear in the active kitchen queue.");
        assertTrue(orders.findKitchenQueue().stream().anyMatch(row -> row.startsWith(orderId + "  |")),
                "A newly placed pending order must appear in the kitchen-facing queue.");
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT stock_quantity FROM ingredients WHERE name='Chicken'"); ResultSet rs=ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(1500d,rs.getDouble(1),0.0001);
        }
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT quantity_change FROM ingredient_movements WHERE order_id=?")) {
            ps.setString(1,orderId); try(ResultSet rs=ps.executeQuery()){assertTrue(rs.next());assertEquals(-500d,rs.getDouble(1),0.0001);}
        }
    }

    @Test void insufficientStockRollsBackOrderAndInventory() throws Exception {
        RecipeCatalogRepository catalog=new RecipeCatalogRepository();
        catalog.saveCustom("Low Stock Dish","MAIN_COURSE","Cook","Rice | 300 | g");
        long recipeId=catalog.findAll().get(0).id();
        new IngredientRepository().save("Rice",400,"g");
        new MenuPeriodRepository().scheduleRecipe(recipeId,180,10,"LUNCH",LocalDate.now().toString(),LocalDate.now().toString());
        OrderRepository orders=new OrderRepository();
        assertThrows(IllegalStateException.class,()->orders.placeOrder("Test",java.util.Map.of("Low Stock Dish",2)));
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT stock_quantity FROM ingredients WHERE name='Rice'"); ResultSet rs=ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(400d,rs.getDouble(1),0.0001);
        }
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM restaurant_orders"); ResultSet rs=ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(0,rs.getInt(1));
        }
    }

    @Test void manualRecipeWithVagueMeasuresCanBeScheduledOrderedAndDoesNotBlockKitchen() throws Exception {
        RecipeCatalogRepository catalog=new RecipeCatalogRepository();
        catalog.saveCustom("Herb Chicken With Vague Measures","MAIN_COURSE","Season to taste",
                "Chicken | 150 g\nCoriander | 1 handful\nLime | Juice of 3 limes\nSalt | to taste");
        long recipeId=catalog.findAll().stream().filter(r->r.name().equals("Herb Chicken With Vague Measures")).findFirst().orElseThrow().id();
        new IngredientRepository().save("Chicken",500,"g");
        catalog.addMissingInventoryIngredients(recipeId);
        new MenuPeriodRepository().scheduleRecipe(recipeId,350,20,"DINNER",LocalDate.now().toString(),LocalDate.now().plusDays(1).toString());

        OrderRepository orders=new OrderRepository();
        String orderId=assertDoesNotThrow(()->orders.placeOrder("Vague Measure Customer",java.util.Map.of("Herb Chicken With Vague Measures",2)));
        assertTrue(orders.findActiveQueue().stream().anyMatch(row->row.startsWith(orderId+"  |")));
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT stock_quantity FROM ingredients WHERE name='Chicken'"); ResultSet rs=ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(200d,rs.getDouble(1),0.0001,"Only the measurable chicken quantity should be deducted.");
        }
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("SELECT stock_quantity FROM ingredients WHERE name='Salt'"); ResultSet rs=ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(0d,rs.getDouble(1),0.0001,"Vague salt-to-taste quantity must not be guessed or deducted.");
        }
    }

    @Test void mealDbRecipeWithOnlyDescriptiveMeasuresCanBeScheduledAndOrdered() throws Exception {
        RecipeCatalogRepository catalog=new RecipeCatalogRepository();
        catalog.saveMealDb(new MealRecipe("VAGUE-001","Coriander Lime Salad","Side","Test","Mix to taste","",List.of(
                new MealRecipe.Ingredient("Coriander","1 handful"),
                new MealRecipe.Ingredient("Lime","Juice of 3 limes"),
                new MealRecipe.Ingredient("Salt","to taste"))));
        long recipeId=catalog.findAll().stream().filter(r->r.name().equals("Coriander Lime Salad")).findFirst().orElseThrow().id();
        catalog.addMissingInventoryIngredients(recipeId);
        assertDoesNotThrow(()->new MenuPeriodRepository().scheduleRecipe(recipeId,120,8,"LUNCH",LocalDate.now().toString(),LocalDate.now().plusDays(1).toString()));
        OrderRepository orders=new OrderRepository();
        String orderId=assertDoesNotThrow(()->orders.placeOrder("Descriptive Measure Customer",java.util.Map.of("Coriander Lime Salad",1)));
        assertTrue(orders.findActiveQueue().stream().anyMatch(row->row.startsWith(orderId+"  |")));
    }

    @Test void reservationCanBeCreatedListedAndCancelled() throws Exception {
        ReservationRepository repo=new ReservationRepository();
        long id=repo.create("Test Guest","09170000000",4,"2026-12-20T18:00","T1","Window seat");
        assertTrue(repo.findUpcoming().stream().anyMatch(r->r.id()==id && r.guest().equals("Test Guest")));
        repo.updateStatus(id,"CANCELLED");
        assertFalse(repo.findUpcoming().stream().anyMatch(r->r.id()==id));
        assertThrows(IllegalArgumentException.class,()->repo.create("", "", 0, "", "", ""));
    }
    @Test void resetRestaurantDataClearsBusinessRecordsButPreservesStaffAccounts() throws Exception {
        AuthRepository auth = new AuthRepository();
        auth.seedDemoAccounts();
        addMenu("Reset Test Dish");
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ingredients(name,stock_quantity,stock_unit) VALUES('Reset Test Chicken',1000,'g')")) {
            ps.executeUpdate();
        }
        Database.resetRestaurantData();
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM menu_items");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(0, rs.getInt(1));
        }
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM ingredients");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next()); assertEquals(0, rs.getInt(1));
        }
        assertNotNull(auth.authenticate("admin", "admin123"));
    }


    @Test void ingredientQuickAdjustmentAddsAndRemovesStockWithoutChangingUnit() throws Exception {
        IngredientRepository repo = new IngredientRepository();
        repo.save("Bean Sprouts", 280, "g");
        repo.adjustStock("bean sprouts", 20, true);
        var afterAdd = repo.findAll().stream().filter(i -> i.name().equals("Bean Sprouts")).findFirst().orElseThrow();
        assertEquals(300d, afterAdd.quantity(), 0.0001);
        assertEquals("g", afterAdd.unit());

        repo.adjustStock("Bean Sprouts", 50, false);
        var afterRemove = repo.findAll().stream().filter(i -> i.name().equals("Bean Sprouts")).findFirst().orElseThrow();
        assertEquals(250d, afterRemove.quantity(), 0.0001);
        assertEquals("g", afterRemove.unit());
    }

    @Test void ingredientQuickAdjustmentRejectsZeroNegativeAndExcessRemoval() throws Exception {
        IngredientRepository repo = new IngredientRepository();
        repo.save("Rice", 100, "g");
        assertThrows(IllegalArgumentException.class, () -> repo.adjustStock("Rice", 0, true));
        assertThrows(IllegalArgumentException.class, () -> repo.adjustStock("Rice", -5, true));
        assertThrows(IllegalArgumentException.class, () -> repo.adjustStock("Rice", 101, false));
        var rice = repo.findAll().stream().filter(i -> i.name().equals("Rice")).findFirst().orElseThrow();
        assertEquals(100d, rice.quantity(), 0.0001, "Rejected adjustments must not change stock.");
    }

    @Test void seededStaffRolesAuthenticateAndInvalidCredentialsAreRejected() throws Exception {
        AuthRepository repo = new AuthRepository();
        repo.seedDemoAccounts();
        assertEquals("ADMIN", repo.authenticate("ADMIN", "admin123").role());
        assertEquals("SUPERVISOR", repo.authenticate("supervisor", "super123").role());
        assertEquals("WAITER", repo.authenticate("waiter", "waiter123").role());
        assertEquals("CASHIER", repo.authenticate("cashier", "cashier123").role());
        assertEquals("KITCHEN", repo.authenticate("kitchen", "kitchen123").role());
        assertNull(repo.authenticate("admin", "wrong-password"));
        assertNull(repo.authenticate("unknown-user", "admin123"));
        assertNull(repo.authenticate(null, "admin123"));
    }

    @Test void inactiveStaffAccountCannotAuthenticate() throws Exception {
        AuthRepository repo = new AuthRepository();
        repo.seedDemoAccounts();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE staff_users SET active=0 WHERE username='waiter'")) {
            assertEquals(1, ps.executeUpdate());
        }
        assertNull(repo.authenticate("waiter", "waiter123"));
    }

    @Test void cashierTotalsSumOrderItemsAndExcludeCancelledOrders() throws Exception {
        addTimedOrder("CASHIER-PAID", "Cashier Test Dish", 2, "COMPLETED");
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE order_items SET quantity=3 WHERE order_id='CASHIER-PAID'")) {
            ps.executeUpdate();
        }
        addTimedOrder("CASHIER-CANCELLED", "Cancelled Cashier Dish", 2, "CANCELLED");
        var totals = new OrderRepository().findCashierTotals();
        assertTrue(totals.stream().anyMatch(row -> row.contains("CASHIER-PAID") && row.contains("₱750.00")));
        assertFalse(totals.stream().anyMatch(row -> row.contains("CASHIER-CANCELLED")));
    }

    @Test void cashierCanMarkAnOrderPaidWithoutChangingKitchenStatus() throws Exception {
        addTimedOrder("CASHIER-MARK-PAID", "Payment Status Test Dish", 2, "COMPLETED");
        OrderRepository repo = new OrderRepository();

        assertTrue(repo.findCashierTotals().stream()
                .anyMatch(row -> row.contains("CASHIER-MARK-PAID") && row.contains("PAYMENT: UNPAID")));

        repo.markOrderPaid("CASHIER-MARK-PAID");

        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status, payment_status, paid_at FROM restaurant_orders WHERE order_id=?")) {
            ps.setString(1, "CASHIER-MARK-PAID");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("COMPLETED", rs.getString("status"),
                        "Recording payment must not change the kitchen order status.");
                assertEquals("PAID", rs.getString("payment_status"));
                assertNotNull(rs.getString("paid_at"));
            }
        }
        assertTrue(repo.findCashierTotals().stream()
                .anyMatch(row -> row.contains("CASHIER-MARK-PAID") && row.contains("PAYMENT: PAID")));
        assertThrows(IllegalStateException.class, () -> repo.markOrderPaid("CASHIER-MARK-PAID"));
    }

    @Test void waiterCanMarkOnlyKitchenCompletedOrdersAsServed() throws Exception {
        addTimedOrder("WAITER-READY-TO-SERVE", "Waiter Service Test Dish", 2, "COMPLETED");
        OrderRepository repo = new OrderRepository();

        assertTrue(repo.findOrdersReadyToServe().stream()
                .anyMatch(row -> row.contains("WAITER-READY-TO-SERVE")));
        repo.markOrderServed("WAITER-READY-TO-SERVE");

        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status, service_status, served_at FROM restaurant_orders WHERE order_id=?")) {
            ps.setString(1, "WAITER-READY-TO-SERVE");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("COMPLETED", rs.getString("status"),
                        "Waiter service must not change the kitchen order status.");
                assertEquals("SERVED", rs.getString("service_status"));
                assertNotNull(rs.getString("served_at"));
            }
        }
        assertFalse(repo.findOrdersReadyToServe().stream()
                .anyMatch(row -> row.contains("WAITER-READY-TO-SERVE")));
        assertThrows(IllegalStateException.class, () -> repo.markOrderServed("WAITER-READY-TO-SERVE"));
    }

    @Test void waiterCannotMarkAnOrderServedBeforeKitchenCompletesIt() throws Exception {
        addTimedOrder("WAITER-NOT-READY", "Waiter Not Ready Test Dish", 1, "PREPARING");
        assertThrows(IllegalStateException.class,
                () -> new OrderRepository().markOrderServed("WAITER-NOT-READY"));
    }

    @Test void reservationRejectsUnsupportedStatus() throws Exception {
        ReservationRepository repo = new ReservationRepository();
        long id = repo.create("Status Test Guest", "", 2, "2026-12-21T18:00", "T2", "");
        assertThrows(IllegalArgumentException.class, () -> repo.updateStatus(id, "WAITING"));
        assertTrue(repo.findUpcoming().stream().anyMatch(r -> r.id() == id && "BOOKED".equals(r.status())));
    }


    private long addOrderableMenu(String name, long priceCents, int prepMinutes) throws Exception {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO menu_items(name,item_type,price,price_cents,base_prep_minutes,
                                        available_today,daily_menu_date)
                VALUES(?,'MAIN_COURSE',CAST(? AS REAL),?,?,1,date('now','localtime'))
                """)) {
            ps.setString(1, name);
            ps.setString(2, ph.uc.cpe314.dinesmart.util.Money.toDatabaseDecimal(priceCents));
            ps.setLong(3, priceCents);
            ps.setInt(4, prepMinutes);
            ps.executeUpdate();
        }
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT menu_item_id FROM menu_items WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    @Test void fullOrderWorkflowMovesFromPendingThroughKitchenServiceAndPayment() throws Exception {
        addOrderableMenu("Workflow Chicken Pasta", 18500L, 12);
        OrderRepository repo = new OrderRepository();
        AuthRepository auth = new AuthRepository();
        long staffId = auth.createStaffUser("workflowwaiter", "workflow123",
                "Workflow Waiter", "WAITER");
        String orderId = repo.placeOrder("Workflow Customer",
                java.util.Map.of("Workflow Chicken Pasta", 2), "Table 5", staffId);

        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT o.table_id, o.created_by, t.table_label
                     FROM restaurant_orders o
                     LEFT JOIN restaurant_tables t ON t.table_id = o.table_id
                     WHERE o.order_id = ?
                     """)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(staffId, rs.getLong("created_by"));
                assertEquals("Table 5", rs.getString("table_label"));
                assertTrue(rs.getLong("table_id") > 0);
            }
        }
        assertTrue(repo.findOrdersCreatedByToday(staffId).stream()
                .anyMatch(row -> row.contains(orderId)));
        assertTrue(repo.findKitchenQueue().stream().anyMatch(row -> row.contains(orderId) && row.contains("PENDING")));
        repo.changeStatus(orderId, "PREPARING");
        assertTrue(repo.findKitchenQueue().stream().anyMatch(row -> row.contains(orderId) && row.contains("PREPARING")));
        repo.changeStatus(orderId, "COMPLETED");
        assertTrue(repo.findOrdersReadyToServe().stream().anyMatch(row -> row.contains(orderId)));

        repo.markOrderServed(orderId);
        repo.markOrderPaid(orderId);
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT status,payment_status,service_status FROM restaurant_orders WHERE order_id=?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("COMPLETED", rs.getString("status"));
                assertEquals("PAID", rs.getString("payment_status"));
                assertEquals("SERVED", rs.getString("service_status"));
            }
        }
        assertFalse(repo.findKitchenQueue().stream().anyMatch(row -> row.contains(orderId)));
        assertFalse(repo.findOrdersReadyToServe().stream().anyMatch(row -> row.contains(orderId)));
        assertTrue(new OrderRepository().findCashierTotals().stream()
                .anyMatch(row -> row.contains(orderId) && row.contains("PAYMENT: PAID")
                        && row.contains("₱370.00")));
        assertEquals(37000L, repo.getTodaySalesByStaffCents().get("Workflow Waiter").longValue());
    }

    @Test void paymentCanBeRecordedBeforeKitchenCompletionWithoutRemovingActiveOrder() throws Exception {
        addTimedOrder("PAYMENT-BEFORE-KITCHEN", "Payment Before Kitchen Dish", 1, "PENDING");
        OrderRepository repo = new OrderRepository();
        repo.markOrderPaid("PAYMENT-BEFORE-KITCHEN");
        assertTrue(repo.findKitchenQueue().stream().anyMatch(row -> row.contains("PAYMENT-BEFORE-KITCHEN")));
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT status,payment_status FROM restaurant_orders WHERE order_id=?")) {
            ps.setString(1, "PAYMENT-BEFORE-KITCHEN");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("PENDING", rs.getString("status"));
                assertEquals("PAID", rs.getString("payment_status"));
            }
        }
    }

    @Test void cancelledOrdersCannotBeMarkedPaid() throws Exception {
        addTimedOrder("CANCELLED-UNPAID", "Cancelled Payment Dish", 1, "CANCELLED");
        OrderRepository repo = new OrderRepository();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repo.markOrderPaid("CANCELLED-UNPAID"));
        assertTrue(error.getMessage().toLowerCase().contains("cancelled"));
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT payment_status FROM restaurant_orders WHERE order_id=?")) {
            ps.setString(1, "CANCELLED-UNPAID");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("UNPAID", rs.getString(1));
            }
        }
    }

    @Test void paymentRejectsBlankAndUnknownOrderIdentifiers() {
        OrderRepository repo = new OrderRepository();
        assertThrows(IllegalArgumentException.class, () -> repo.markOrderPaid("  "));
        assertThrows(IllegalArgumentException.class, () -> repo.markOrderPaid(null));
        assertThrows(IllegalStateException.class, () -> repo.markOrderPaid("NO-SUCH-ORDER"));
    }

    @Test void servingRejectsBlankUnknownAndCancelledOrders() throws Exception {
        addTimedOrder("CANCELLED-SERVICE", "Cancelled Service Dish", 1, "CANCELLED");
        OrderRepository repo = new OrderRepository();
        assertThrows(IllegalArgumentException.class, () -> repo.markOrderServed(""));
        assertThrows(IllegalArgumentException.class, () -> repo.markOrderServed(null));
        assertThrows(IllegalStateException.class, () -> repo.markOrderServed("NO-SUCH-ORDER"));
        assertThrows(IllegalStateException.class, () -> repo.markOrderServed("CANCELLED-SERVICE"));
    }

    @Test void servingDoesNotAutomaticallyMarkAnOrderPaid() throws Exception {
        addTimedOrder("SERVED-UNPAID", "Served Unpaid Dish", 1, "COMPLETED");
        OrderRepository repo = new OrderRepository();
        repo.markOrderServed("SERVED-UNPAID");
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT payment_status,service_status FROM restaurant_orders WHERE order_id=?")) {
            ps.setString(1, "SERVED-UNPAID");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("UNPAID", rs.getString("payment_status"));
                assertEquals("SERVED", rs.getString("service_status"));
            }
        }
    }

    @Test void readyToServeListContainsOnlyCompletedOrdersAwaitingService() throws Exception {
        addTimedOrder("QUEUE-PENDING", "Queue Pending Dish", 1, "PENDING");
        addTimedOrder("QUEUE-PREPARING", "Queue Preparing Dish", 1, "PREPARING");
        addTimedOrder("QUEUE-COMPLETED", "Queue Completed Dish", 1, "COMPLETED");
        addTimedOrder("QUEUE-CANCELLED", "Queue Cancelled Dish", 1, "CANCELLED");
        OrderRepository repo = new OrderRepository();
        repo.markOrderServed("QUEUE-COMPLETED");
        List<String> ready = repo.findOrdersReadyToServe();
        assertFalse(ready.stream().anyMatch(row -> row.contains("QUEUE-PENDING")));
        assertFalse(ready.stream().anyMatch(row -> row.contains("QUEUE-PREPARING")));
        assertFalse(ready.stream().anyMatch(row -> row.contains("QUEUE-COMPLETED")));
        assertFalse(ready.stream().anyMatch(row -> row.contains("QUEUE-CANCELLED")));
    }

    @Test void kitchenQueueFollowsFifoAndRejectsInvalidTransitions() throws Exception {
        addTimedOrder("FIFO-NEWER", "FIFO Newer Dish", 1, "PENDING");
        addTimedOrder("FIFO-OLDER", "FIFO Older Dish", 5, "PENDING");
        OrderRepository repo = new OrderRepository();
        assertEquals(List.of("FIFO-OLDER", "FIFO-NEWER"), repo.findPendingOrderIdsOldestFirst());
        assertThrows(IllegalStateException.class, () -> repo.changeStatus("FIFO-NEWER", "PREPARING"));
        repo.changeStatus("FIFO-OLDER", "PREPARING");
        repo.changeStatus("FIFO-NEWER", "PREPARING");
        assertThrows(IllegalStateException.class, () -> repo.changeStatus("FIFO-OLDER", "PREPARING"));
        assertThrows(IllegalStateException.class, () -> repo.changeStatus("FIFO-OLDER", "CANCELLED"));
        assertThrows(IllegalArgumentException.class, () -> repo.changeStatus("FIFO-NEWER", "SERVED"));
    }

    @Test void cancellingAnOrderRestoresDeductedIngredientsExactlyOnce() throws Exception {
        addOrderableMenu("Cancellation Stock Dish", 9900L, 5);
        IngredientRepository ingredients = new IngredientRepository();
        ingredients.save("Cancellation Test Rice", 500, "g");
        ingredients.setRecipeRequirement("Cancellation Stock Dish", "Cancellation Test Rice", 100, "g");
        OrderRepository repo = new OrderRepository();
        String orderId = repo.placeOrder("Cancel Customer", java.util.Map.of("Cancellation Stock Dish", 2));
        assertEquals(300d, ingredientQuantity("Cancellation Test Rice"), 0.0001);

        repo.cancelPendingOrder(orderId);
        assertEquals(500d, ingredientQuantity("Cancellation Test Rice"), 0.0001);
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT reason, quantity_change
                     FROM ingredient_movements
                     WHERE order_id = ?
                     ORDER BY movement_id
                     """)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ORDER_USAGE", rs.getString("reason"));
                assertEquals(-200d, rs.getDouble("quantity_change"), 0.0001);
                assertTrue(rs.next());
                assertEquals("ORDER_CANCEL_RESTORE", rs.getString("reason"));
                assertEquals(200d, rs.getDouble("quantity_change"), 0.0001);
                assertFalse(rs.next());
            }
        }
        assertThrows(IllegalStateException.class, () -> repo.cancelPendingOrder(orderId));
        assertEquals(500d, ingredientQuantity("Cancellation Test Rice"), 0.0001);
    }

    private double ingredientQuantity(String name) throws Exception {
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT stock_quantity FROM ingredients WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getDouble(1);
            }
        }
    }

    @Test void completedSalesAndBestSellerReportsExcludeCancelledAndUnfinishedOrders() throws Exception {
        addTimedOrder("REPORT-COMPLETED", "Report Completed Dish", 2, "COMPLETED");
        addTimedOrder("REPORT-PENDING", "Report Pending Dish", 2, "PENDING");
        addTimedOrder("REPORT-CANCELLED", "Report Cancelled Dish", 2, "CANCELLED");
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE order_items SET quantity=2 WHERE order_id='REPORT-COMPLETED'")) {
            ps.executeUpdate();
        }
        OrderRepository repo = new OrderRepository();
        assertEquals(50000L, repo.getTodaySalesCents());
        assertEquals(1, repo.getTodayOrderCount());
        assertTrue(repo.findBestSellers(5).stream().anyMatch(item ->
                item.name().equals("Report Completed Dish") && item.quantity() == 2));
        assertFalse(repo.findBestSellers(5).stream().anyMatch(item ->
                item.name().equals("Report Pending Dish") || item.name().equals("Report Cancelled Dish")));
    }

    @Test void orderHourReportContainsAllTwentyFourHoursAndExcludesCancelledOrders() throws Exception {
        addTimedOrder("HOUR-COMPLETED", "Hour Completed Dish", 1, "COMPLETED");
        addTimedOrder("HOUR-PENDING", "Hour Pending Dish", 1, "PENDING");
        addTimedOrder("HOUR-CANCELLED", "Hour Cancelled Dish", 1, "CANCELLED");
        var counts = new OrderRepository().getOrdersByHour();
        assertEquals(24, counts.size());
        assertEquals(2, counts.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(counts.keySet().containsAll(java.util.stream.IntStream.range(0, 24).boxed().toList()));
    }

    @Test void menuOrderKeepsTheOriginalPriceEvenIfTheCatalogPriceChangesLater() throws Exception {
        addOrderableMenu("Historical Price Dish", 15000L, 8);
        String orderId = new OrderRepository().placeOrder("Price Snapshot Customer",
                java.util.Map.of("Historical Price Dish", 2));
        new MenuItemRepository().updatePriceByName("Historical Price Dish", 17500L);
        assertTrue(new OrderRepository().findCashierTotals().stream()
                .anyMatch(row -> row.contains(orderId) && row.contains("₱300.00")));
        assertTrue(new MenuItemRepository().findAllPriceSummaries().stream()
                .anyMatch(row -> row.contains("Historical Price Dish") && row.contains("₱175.00")));
    }



    @Test
    void menuCategoryFrequencyReturnsEveryCategoryIncludingZeroCounts() throws Exception {
        addOrderableMenu("Category Main Dish", 2500L, 10);
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO menu_items
                         (name, item_type, price, price_cents, base_prep_minutes,
                          available_today, daily_menu_date)
                     VALUES ('Category Starter', 'APPETIZER', 75, 7500, 5, 1,
                             date('now','localtime'))
                     """)) {
            ps.executeUpdate();
        }

        var counts = new MenuItemRepository().countByItemType();

        assertEquals(1, counts.get("MAIN_COURSE").intValue());
        assertEquals(1, counts.get("APPETIZER").intValue());
        assertEquals(0, counts.get("BEVERAGE").intValue());
    }

    @Test
    void orderTotalsUseExactIntegerCentsInsteadOfFloatingPoint() throws Exception {
        addOrderableMenu("Exact Cent Dish", 310L, 5);
        OrderRepository repository = new OrderRepository();
        String orderId = repository.placeOrder("Exact Cent Customer",
                java.util.Map.of("Exact Cent Dish", 3));

        assertTrue(repository.findCashierTotals().stream()
                .anyMatch(row -> row.contains(orderId) && row.contains("₱9.30")));
    }

    @Test void reservationCannotDoubleBookTheSameNamedTableAtTheSameTime() throws Exception {
        ReservationRepository repo = new ReservationRepository();
        repo.create("First Guest", "", 2, "2026-12-22T18:00", "Table 4", "");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repo.create("Second Guest", "", 3, "2026-12-22T18:00", "table 4", ""));
        assertTrue(error.getMessage().toLowerCase().contains("already reserved"));
        var saved = repo.findUpcoming().stream()
                .filter(r -> r.table().equals("Table 4")).findFirst().orElseThrow();
        assertNotNull(saved.tableId());
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT table_label FROM restaurant_tables WHERE table_id = ?")) {
            ps.setLong(1, saved.tableId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Table 4", rs.getString(1));
            }
        }
        assertEquals(1, repo.findUpcoming().stream().filter(r -> r.table().equals("Table 4")).count());
    }

    @Test void reservationsAllowDifferentTablesAndMultipleUnassignedBookingsAtSameTime() throws Exception {
        ReservationRepository repo = new ReservationRepository();
        String slot = "2026-12-23T19:00";
        repo.create("Guest One", "", 2, slot, "Table 1", "");
        repo.create("Guest Two", "", 2, slot, "Table 2", "");
        repo.create("Guest Three", "", 2, slot, "Unassigned", "");
        repo.create("Guest Four", "", 2, slot, "", "");
        assertEquals(4, repo.findUpcoming().size());
    }

    @Test void cancelledReservationReleasesTheTableForTheSameTimeSlot() throws Exception {
        ReservationRepository repo = new ReservationRepository();
        long first = repo.create("Cancel Guest", "", 2, "2026-12-24T20:00", "Table 7", "");
        repo.updateStatus(first, "CANCELLED");
        long replacement = repo.create("Replacement Guest", "", 2, "2026-12-24T20:00", "Table 7", "");
        assertTrue(replacement > first);
        assertTrue(repo.findUpcoming().stream().anyMatch(r -> r.id() == replacement));
    }

    @Test void upcomingReservationsIncludeBookedAndSeatedButExcludeCancelledAndCompleted() throws Exception {
        ReservationRepository repo = new ReservationRepository();
        long booked = repo.create("Booked Guest", "", 2, "2026-12-25T18:00", "T1", "");
        long seated = repo.create("Seated Guest", "", 2, "2026-12-25T18:15", "T2", "");
        long cancelled = repo.create("Cancelled Guest", "", 2, "2026-12-25T18:30", "T3", "");
        long completed = repo.create("Completed Guest", "", 2, "2026-12-25T18:45", "T4", "");
        repo.updateStatus(seated, "SEATED");
        repo.updateStatus(cancelled, "CANCELLED");
        repo.updateStatus(completed, "COMPLETED");
        List<ReservationRepository.Reservation> upcoming = repo.findUpcoming();
        assertTrue(upcoming.stream().anyMatch(r -> r.id() == booked));
        assertTrue(upcoming.stream().anyMatch(r -> r.id() == seated));
        assertFalse(upcoming.stream().anyMatch(r -> r.id() == cancelled));
        assertFalse(upcoming.stream().anyMatch(r -> r.id() == completed));
    }

    @Test void reservationUpdateRejectsMissingReservationId() {
        ReservationRepository repo = new ReservationRepository();
        assertThrows(java.sql.SQLException.class, () -> repo.updateStatus(999999, "CANCELLED"));
    }

    @Test void databaseInitializationCanRunRepeatedlyWithoutLosingBusinessData() throws Exception {
        addMenu("Idempotent Initialization Dish");
        Database.initialize();
        Database.initialize();
        assertTrue(new MenuItemRepository().findAllSummaries().stream()
                .anyMatch(row -> row.contains("Idempotent Initialization Dish")));
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='table_reservations'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

}
