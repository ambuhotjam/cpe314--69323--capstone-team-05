
package ph.uc.cpe314.dinesmart.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;
import javafx.scene.text.TextAlignment;
import javafx.scene.control.*;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import ph.uc.cpe314.dinesmart.data.Database;
import ph.uc.cpe314.dinesmart.data.ComboRepository;
import ph.uc.cpe314.dinesmart.data.MealRecipeRepository;
import ph.uc.cpe314.dinesmart.data.MenuItemRepository;
import ph.uc.cpe314.dinesmart.data.OrderRepository;
import ph.uc.cpe314.dinesmart.model.*;
import ph.uc.cpe314.dinesmart.service.KitchenQueue;
import ph.uc.cpe314.dinesmart.service.PendingOrderStack;
import ph.uc.cpe314.dinesmart.service.PersistentKitchenQueue;
import ph.uc.cpe314.dinesmart.service.TheMealDbService;

import java.util.concurrent.CompletableFuture;

public class DineSmartApp extends Application {

    private final TheMealDbService api = new TheMealDbService();

    private final MealRecipeRepository recipeRepository =
            new MealRecipeRepository();

    private final MenuItemRepository menuRepository =
            new MenuItemRepository();
    private final ComboRepository comboRepository = new ComboRepository();
    private final OrderRepository orderRepository = new OrderRepository();
    private Label mainCourseMetric;
    private Label appetizerMetric;
    private Label beverageMetric;
    private Label comboMetric;
    private ListView<String> beverageList;

    @Override
    public void start(Stage stage) {

        Label title = new Label(
                "DineSmart — Restaurant Ordering & Inventory Insights");
        title.getStyleClass().add("app-title");

        Label status = new Label(
                "Search recipes, import them, and manage restaurant prices.");
        status.getStyleClass().add("status-banner");

        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.setPrefRowCount(5);

        // Live data-mining visualization: count actual saved menu items by type.
        CategoryAxis categoryAxis = new CategoryAxis();
        categoryAxis.setLabel("Menu Category");
        NumberAxis countAxis = new NumberAxis();
        countAxis.setLabel("Number of Saved Items");
        BarChart<String, Number> menuChart =
                new BarChart<>(categoryAxis, countAxis);
        menuChart.setTitle("DineSmart Menu Category Frequency");
        menuChart.setLegendVisible(false);
        menuChart.setPrefHeight(280);
        Button refreshChartButton = new Button("Refresh Menu Insights");
        refreshChartButton.setOnAction(event -> {
            try {
                refreshMenuChart(menuChart);
                status.setText("Menu insights updated from live SQLite data.");
            } catch (Exception ex) {
                status.setText("Could not generate menu insights.");
                output.setText(message(ex));
            }
        });

        // Database initialization button
        Button dbButton =
                new Button("Initialize Database & Load Sample Menu");

        // Menu management controls
        ListView<String> menuList = new ListView<>();
        menuList.setPrefHeight(150);

        TextField menuSearchField = new TextField();
        menuSearchField.setPromptText("Search saved menu by name");

        Button searchSavedMenuButton =
                new Button("Search Saved Menus");

        Button showAllMenusButton =
                new Button("Show All Menus");
        ChoiceBox<String> savedCategoryChoice = new ChoiceBox<>();
        savedCategoryChoice.getItems().addAll("All Categories", "Main Course", "Appetizer", "Beverage");
        savedCategoryChoice.setValue("All Categories");
        Button filterCategoryButton = new Button("Filter Category");
        filterCategoryButton.setOnAction(event -> {
            try {
                String choice = savedCategoryChoice.getValue();
                if ("All Categories".equals(choice)) refreshMenu(menuList);
                else menuList.getItems().setAll(menuRepository.findByTypeSummaries(choice.toUpperCase().replace(' ', '_')));
                status.setText("Showing " + choice + " menu items.");
            } catch (Exception ex) { status.setText("Could not filter menu category."); output.setText(message(ex)); }
        });

        TextField priceField = new TextField();
        priceField.setPromptText("Enter selling price, e.g. 199.00");

        Button refreshMenuButton = new Button("Refresh Menu");
        Button updatePriceButton = new Button("Set / Update Price");
        Button deleteMenuButton = new Button("Delete Selected Menu");
        deleteMenuButton.getStyleClass().add("button-danger");
        showAllMenusButton.getStyleClass().add("button-secondary");
        refreshMenuButton.getStyleClass().add("button-secondary");
        refreshChartButton.getStyleClass().add("button-secondary");

        // Beverage items are maintained directly by the restaurant in SQLite.
        TextField beverageNameField = new TextField();
        beverageNameField.setPromptText("Beverage name, e.g. Mango Juice");
        TextField beveragePriceField = new TextField();
        beveragePriceField.setPromptText("Selling price");
        Button addBeverageButton = new Button("Add Beverage");
        addBeverageButton.setOnAction(event -> {
            try {
                String name = beverageNameField.getText().trim();
                double price = Double.parseDouble(beveragePriceField.getText().trim());
                menuRepository.insert(new Beverage(name, price, 2));
                beverageNameField.clear(); beveragePriceField.clear();
                refreshMenu(menuList); refreshMenuChart(menuChart);
                if (beverageList != null) beverageList.getItems().setAll(menuRepository.findByTypeSummaries("BEVERAGE"));
                status.setText("Beverage added to SQLite: " + name);
                output.setText("Saved beverage: " + name + " | ₱" + String.format("%.2f", price));
            } catch (NumberFormatException ex) {
                status.setText("Enter a valid beverage price.");
            } catch (Exception ex) {
                status.setText("Could not add beverage."); output.setText(message(ex));
            }
        });

        // Combos are stored separately and priced from their component menu items.
        TextField comboNameField = new TextField();
        comboNameField.setPromptText("Combo name, e.g. Chicken Meal Combo");
        TextField comboItemsField = new TextField();
        comboItemsField.setPromptText("Items and quantities: Chicken Adobo Rice:1, Calamansi Juice:1");
        TextField comboDiscountField = new TextField("0");
        comboDiscountField.setPromptText("Discount %");
        ListView<String> comboList = new ListView<>();
        comboList.setPrefHeight(90);
        Button createComboButton = new Button("Create Combo");
        Button refreshCombosButton = new Button("Refresh Combos");
        refreshCombosButton.getStyleClass().add("button-secondary");
        Runnable refreshCombos = () -> {
            try { comboList.getItems().setAll(comboRepository.findAllSummaries()); }
            catch (Exception ex) { status.setText("Could not load combos."); output.setText(message(ex)); }
        };
        refreshCombosButton.setOnAction(event -> refreshCombos.run());
        createComboButton.setOnAction(event -> {
            try {
                java.util.Map<String, Integer> components = new java.util.LinkedHashMap<>();
                for (String part : comboItemsField.getText().split(",")) {
                    String[] pair = part.trim().split(":");
                    if (pair.length != 2) throw new IllegalArgumentException("Use Item Name:Quantity, separated by commas.");
                    components.put(pair[0].trim(), Integer.parseInt(pair[1].trim()));
                }
                double discount = Double.parseDouble(comboDiscountField.getText().trim());
                comboRepository.createCombo(comboNameField.getText(), components, discount);
                refreshCombos.run(); refreshMenuChart(menuChart); refreshDashboardMetrics();
                double sellingPrice = comboRepository.calculateSellingPrice(comboNameField.getText().trim());
                status.setText("Combo created successfully.");
                output.setText("Combo: " + comboNameField.getText().trim() + "\nDiscount: " + discount + "%\nSelling price: ₱" + String.format("%.2f", sellingPrice));
                comboNameField.clear(); comboItemsField.clear(); comboDiscountField.setText("0");
            } catch (Exception ex) { status.setText("Could not create combo. Check item names and quantities."); output.setText(message(ex)); }
        });

        // Customer ordering, persistent kitchen queue, and order lifecycle.
        CategoryAxis salesCategoryAxis = new CategoryAxis();
        salesCategoryAxis.setLabel("Best-selling menu item");
        NumberAxis salesQuantityAxis = new NumberAxis();
        salesQuantityAxis.setLabel("Quantity sold");
        BarChart<String, Number> salesChart = new BarChart<>(salesCategoryAxis, salesQuantityAxis);
        salesChart.setTitle("Best-selling foods & beverages");
        salesChart.setPrefHeight(390);
        Label todaySalesLabel = new Label("₱0.00");
        Label todayOrdersLabel = new Label("0 completed orders");
        TextField customerNameField = new TextField();
        customerNameField.setPromptText("Customer name");
        ComboBox<OrderRepository.MenuChoice> orderItemChoice = new ComboBox<>();
        orderItemChoice.setPromptText("Choose a menu item");
        orderItemChoice.setPrefWidth(360);
        TextField orderQuantityField = new TextField("1");
        orderQuantityField.setPromptText("Qty");
        orderQuantityField.setPrefWidth(85);
        Button addToCartButton = new Button("Add item");
        ListView<String> orderCartList = new ListView<>();
        orderCartList.setPrefHeight(145);
        java.util.Map<String, Integer> orderCart = new java.util.LinkedHashMap<>();
        Label cartTotalLabel = new Label("Order total: ₱0.00");
        cartTotalLabel.getStyleClass().add("order-total");
        Button removeCartItemButton = new Button("Remove selected");
        removeCartItemButton.getStyleClass().add("button-secondary");
        Button clearCartButton = new Button("Clear order");
        clearCartButton.getStyleClass().add("button-secondary");
        Button placeOrderButton = new Button("Place Order & Add to Queue");
        Button refreshQueueButton = new Button("Refresh queue");
        refreshQueueButton.getStyleClass().add("button-secondary");
        Button prepareOrderButton = new Button("Start preparing");
        Button completeOrderButton = new Button("Mark completed");
        Button cancelOrderButton = new Button("Cancel selected order");
        cancelOrderButton.getStyleClass().add("button-danger");
        Button cancelLatestOrderButton = new Button("Cancel most recent pending (Stack)");
        cancelLatestOrderButton.getStyleClass().add("button-danger");
        ListView<String> activeOrdersList = new ListView<>();
        activeOrdersList.setPrefHeight(280);
        PendingOrderStack pendingOrderStack = new PendingOrderStack();
        PersistentKitchenQueue persistentKitchenQueue = new PersistentKitchenQueue();
        ListView<String> orderHistoryList = new ListView<>();
        orderHistoryList.setPrefHeight(180);
        Label orderQueueSummary = new Label("Orders are saved to the local SQLite database.");
        orderQueueSummary.getStyleClass().add("page-subtitle");

        Runnable refreshOrders = () -> {
            try {
                activeOrdersList.getItems().setAll(orderRepository.findActiveQueue());
                java.util.List<String> pendingIds = orderRepository.findPendingOrderIdsOldestFirst();
                pendingOrderStack.rebuild(pendingIds);
                persistentKitchenQueue.rebuild(pendingIds);
                orderHistoryList.getItems().setAll(orderRepository.findOrderHistory());
                orderQueueSummary.setText(activeOrdersList.getItems().size() + " active order(s) in the kitchen queue · FIFO next: " + persistentKitchenQueue.nextOrderId().orElse("none"));
            } catch (Exception ex) { status.setText("Could not refresh orders."); output.setText(message(ex)); }
        };
        Runnable refreshOrderChoices = () -> {
            try { orderItemChoice.getItems().setAll(orderRepository.findOrderableItems()); }
            catch (Exception ex) { status.setText("Could not load menu items for ordering."); output.setText(message(ex)); }
        };
        Runnable updateCartDisplay = () -> {
            orderCartList.getItems().clear();
            double total = 0;
            for (java.util.Map.Entry<String, Integer> line : orderCart.entrySet()) {
                OrderRepository.MenuChoice choice = orderItemChoice.getItems().stream().filter(i -> i.cartKey().equals(line.getKey())).findFirst().orElse(null);
                double price = choice == null ? 0 : choice.price();
                String displayName = choice == null ? line.getKey().replaceFirst("^@COMBO@", "") : choice.name();
                total += price * line.getValue();
                orderCartList.getItems().add(displayName + "  ×  " + line.getValue() + "  |  ₱" + String.format("%.2f", price * line.getValue()));
            }
            cartTotalLabel.setText("Order total: ₱" + String.format("%.2f", total));
        };
        addToCartButton.setOnAction(e -> {
            OrderRepository.MenuChoice choice = orderItemChoice.getValue();
            if (choice == null) { status.setText("Choose a menu item first."); return; }
            try {
                int quantity = Integer.parseInt(orderQuantityField.getText().trim());
                if (quantity < 1) throw new NumberFormatException();
                orderCart.merge(choice.cartKey(), quantity, Integer::sum);
                updateCartDisplay.run();
                status.setText(choice.name() + " added to the customer order.");
            } catch (NumberFormatException ex) { status.setText("Enter a whole-number quantity of at least 1."); }
        });
        removeCartItemButton.setOnAction(e -> {
            String selected = orderCartList.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            String name = selected.substring(0, selected.lastIndexOf("  ×  "));
            String key = orderCart.keySet().stream().filter(k -> k.replaceFirst("^@COMBO@", "").equals(name)).findFirst().orElse(name);
            orderCart.remove(key); updateCartDisplay.run();
        });
        clearCartButton.setOnAction(e -> { orderCart.clear(); updateCartDisplay.run(); });
        placeOrderButton.setOnAction(e -> {
            try {
                String orderId = orderRepository.placeOrder(customerNameField.getText(), orderCart);
                orderCart.clear(); customerNameField.clear(); updateCartDisplay.run();
                refreshOrders.run();
                status.setText("Order " + orderId + " added to the kitchen queue.");
                output.setText("New customer order saved.\nOrder number: " + orderId + "\nStatus: PENDING");
            } catch (Exception ex) { status.setText("Could not place order."); output.setText(message(ex)); }
        });
        refreshQueueButton.setOnAction(e -> { refreshOrders.run(); refreshOrderChoices.run(); });
        java.util.function.BiConsumer<String, String> changeSelectedOrder = (newStatus, actionLabel) -> {
            String selected = activeOrdersList.getSelectionModel().getSelectedItem();
            if (selected == null) { status.setText("Select an order from the active queue first."); return; }
            String orderId = selected.split("\\s+\\|\\s+")[0].trim();
            Runnable apply = () -> {
                try {
                    orderRepository.changeStatus(orderId, newStatus);
                    refreshOrders.run(); refreshBestSellerChart(salesChart); refreshSalesSummary(todaySalesLabel, todayOrdersLabel);
                    status.setText("Order " + orderId + " marked " + newStatus + ".");
                } catch (Exception ex) { status.setText("Could not " + actionLabel.toLowerCase() + " this order."); output.setText(message(ex)); }
            };
            if ("CANCELLED".equals(newStatus)) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Cancel order " + orderId + "? This cannot be undone.", ButtonType.CANCEL, ButtonType.OK);
                confirm.setTitle("Confirm order cancellation"); confirm.setHeaderText("Cancel this customer order?");
                confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(b -> apply.run());
            } else apply.run();
        };
        prepareOrderButton.setOnAction(e -> changeSelectedOrder.accept("PREPARING", "start preparing"));
        completeOrderButton.setOnAction(e -> changeSelectedOrder.accept("COMPLETED", "complete"));
        cancelOrderButton.setOnAction(e -> changeSelectedOrder.accept("CANCELLED", "cancel"));
        cancelLatestOrderButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Cancel the most recently placed pending order?", ButtonType.CANCEL, ButtonType.OK);
            confirm.setTitle("Cancel latest pending order");
            confirm.setHeaderText("Stack-based rush cancellation");
            confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
                try {
                    java.util.Optional<String> latest = pendingOrderStack.popMostRecent();
                    if (latest.isEmpty()) status.setText("There are no pending orders to cancel.");
                    else {
                        orderRepository.cancelPendingOrder(latest.get());
                        refreshOrders.run();
                        status.setText("Most recent pending order cancelled using LIFO Stack: " + latest.get());
                    }
                } catch (Exception ex) { status.setText("Could not cancel latest pending order."); output.setText(message(ex)); }
            });
        });

        // Update the price of a selected menu item
        updatePriceButton.setOnAction(event -> {
            String selected = menuList.getSelectionModel()
                    .getSelectedItem();

            if (selected == null) {
                status.setText("Please select a menu item first.");
                return;
            }

            String priceText = priceField.getText().trim();

            try {
                double price = Double.parseDouble(priceText);

                if (!Double.isFinite(price) || price <= 0) {
                    status.setText(
                            "Enter a valid price greater than ₱0.00.");
                    return;
                }

                int separator = selected.indexOf(" | ");

                if (separator < 1) {
                    status.setText(
                            "Could not identify the selected item.");
                    return;
                }

                String itemName = selected.substring(0, separator);

                menuRepository.updatePriceByName(itemName, price);
                refreshMenu(menuList);

                status.setText("Price updated for " + itemName
                        + " to ₱" + String.format("%.2f", price));

                output.setText(
                        "Price saved successfully.\n"
                        + "Menu item: " + itemName + "\n"
                        + "New selling price: ₱"
                        + String.format("%.2f", price));

            } catch (NumberFormatException ex) {
                status.setText(
                        "Please enter a valid number, e.g. 199.00.");
            } catch (Exception ex) {
                status.setText("Could not update the price.");
                output.setText(message(ex));
            }
        });

        menuList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, selected) -> {
                    if (selected != null) {
                        priceField.clear();
                        status.setText("Selected: " + selected
                                + ". Enter a new selling price.");
                    }
                });

        // Refresh all saved menus
        refreshMenuButton.setOnAction(event -> {
            try {
                refreshMenu(menuList);
                status.setText("Menu refreshed from SQLite.");
            } catch (Exception ex) {
                status.setText("Could not load the menu.");
                output.setText(message(ex));
            }
        });

        // Search saved menu items in SQLite
        searchSavedMenuButton.setOnAction(event -> {
            String query = menuSearchField.getText();

            try {
                if (query == null || query.isBlank()) {
                    refreshMenu(menuList);
                    status.setText("Showing all saved menu items.");
                } else {
                    menuList.getItems().setAll(
                            menuRepository.searchPriceSummaries(query)
                    );

                    status.setText(
                            "Search completed. Matching items: "
                                    + menuList.getItems().size()
                    );

                    if (menuList.getItems().isEmpty()) {
                        output.setText(
                                "No saved menu items matched: " + query);
                    }
                }
            } catch (Exception ex) {
                status.setText("Could not search saved menus.");
                output.setText(message(ex));
            }
        });

        // Show the complete saved menu again
        showAllMenusButton.setOnAction(event -> {
            menuSearchField.clear();

            try {
                refreshMenu(menuList);
                status.setText("Showing all saved menu items.");
            } catch (Exception ex) {
                status.setText("Could not load saved menus.");
                output.setText(message(ex));
            }
        });

        // Delete the selected saved menu item
        deleteMenuButton.setOnAction(event -> {
            String selected = menuList.getSelectionModel()
                    .getSelectedItem();

            if (selected == null) {
                status.setText(
                        "Please select a menu item to delete.");
                return;
            }

            int separator = selected.indexOf(" | ");

            if (separator < 1) {
                status.setText(
                        "Could not identify the selected menu item.");
                return;
            }

            String itemName = selected.substring(0, separator);

            Alert confirmation =
                    new Alert(Alert.AlertType.CONFIRMATION);

            confirmation.setTitle("Delete Menu Item");
            confirmation.setHeaderText(
                    "Delete " + itemName + "?");
            confirmation.setContentText(
                    "This will remove the selected menu item from "
                    + "the saved menu if the database allows it.");

            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        boolean deleted =
                                menuRepository.deleteByName(itemName);

                        if (deleted) {
                            refreshMenu(menuList);
                            refreshMenuChart(menuChart);
                            status.setText(
                                    "Deleted menu item: " + itemName);
                            output.setText(
                                    "Deleted from SQLite:\n" + itemName);
                        } else {
                            status.setText(
                                    "Menu item was not found.");
                        }
                    } catch (Exception ex) {
                        status.setText(
                                "Could not delete the item. It may be "
                                + "referenced by an order or combo.");
                        output.setText(message(ex));
                    }
                }
            });
        });

        // Initialize database and insert sample menu items
        dbButton.setOnAction(event -> {
            try {
                Database.initialize();
                int removedInvalidRows = menuRepository.deleteInvalidRows();

                ph.uc.cpe314.dinesmart.model.MenuItem[] samples = {
                    new Appetizer("Garlic Bread", 89.00, 8),
                    new MainCourse("Chicken Adobo Rice", 159.00, 18),
                    new Beverage("Calamansi Juice", 49.00, 2)
                };

                for (ph.uc.cpe314.dinesmart.model.MenuItem item : samples) {
                    menuRepository.insert(item);
                }

                KitchenQueue queue = new KitchenQueue();
                queue.enqueue(new RestaurantOrder());

                refreshMenu(menuList);
                refreshMenuChart(menuChart);
                refreshCombos.run();

                output.clear();
                output.appendText(
                        "SQLite initialized. Sample menu "
                        + "INSERT/SELECT results:\n");

                for (String item : menuRepository.findAllSummaries()) {
                    output.appendText(item + "\n");
                }

                output.appendText(
                        "\nKitchen queue demonstration: "
                        + queue.size()
                        + " pending sample order(s).\n");

                status.setText(
                        "Database initialized and sample menu loaded.");

            } catch (Exception ex) {
                status.setText("Database operation failed.");
                output.setText(message(ex));
            }
        });

        // TheMealDB recipe search controls
        TextField searchField = new TextField();
        searchField.setPromptText(
                "Search meal name, e.g. Arrabiata");

        Button searchButton = new Button("Search by Name");
        ChoiceBox<String> apiCategoryChoice = new ChoiceBox<>();
        apiCategoryChoice.getItems().addAll("Main Course", "Appetizer (Starter)");
        apiCategoryChoice.setValue("Main Course");
        Button browseCategoryButton = new Button("Browse Category");

        ListView<MealRecipe> results = new ListView<>();
        results.setPrefHeight(125);

        results.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(MealRecipe recipe, boolean empty) {
                super.updateItem(recipe, empty);

                if (empty || recipe == null) {
                    setText(null);
                } else {
                    setText(recipe.name() + " — "
                            + blankAsDash(recipe.category())
                            + " (" + blankAsDash(recipe.area()) + ")");
                }
            }
        });

        TextArea recipeDetails = new TextArea();
        recipeDetails.setEditable(false);
        recipeDetails.setWrapText(true);
        recipeDetails.setPrefRowCount(6);

        Button importButton = new Button(
                "Import Selected Recipe & Ingredients to SQLite");
        importButton.setDisable(true);

        results.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldValue, selected) -> {
                    importButton.setDisable(selected == null);

                    if (selected != null) {
                        recipeDetails.setText(formatRecipe(selected));
                    }
                });

        // Search TheMealDB online
        searchButton.setOnAction(event -> {
            String query = searchField.getText();

            if (query == null || query.isBlank()) {
                status.setText("Enter a meal name first.");
                return;
            }

            searchButton.setDisable(true);
            status.setText("Searching TheMealDB...");
            results.getItems().clear();
            recipeDetails.clear();

            CompletableFuture.supplyAsync(() -> {
                try {
                    return api.searchByName(query.trim());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).whenComplete((meals, error) ->
                    Platform.runLater(() -> {
                        searchButton.setDisable(false);

                        if (error != null) {
                            status.setText(
                                    "API search failed. Check your "
                                    + "internet connection.");
                            output.setText(message(error));
                            return;
                        }

                        results.getItems().setAll(meals);

                        status.setText(meals.isEmpty()
                                ? "No meals found. Try another search."
                                : "Found " + meals.size()
                                + " meal(s). Select one to view details.");

                        if (!meals.isEmpty()) {
                            results.getSelectionModel().selectFirst();
                        }
                    }));
        });

        // Browse TheMealDB categories. Starter is used for appetizers.
        browseCategoryButton.setOnAction(event -> {
            String category = "Appetizer (Starter)".equals(apiCategoryChoice.getValue()) ? "Starter" : "Main Course";
            browseCategoryButton.setDisable(true);
            status.setText("Loading TheMealDB " + category + " category...");
            CompletableFuture.supplyAsync(() -> {
                try { return api.searchByCategory(category); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }).whenComplete((meals, error) -> Platform.runLater(() -> {
                browseCategoryButton.setDisable(false);
                if (error != null) { status.setText("Could not browse category."); output.setText(message(error)); return; }
                results.getItems().setAll(meals);
                status.setText("Found " + meals.size() + " " + category + " recipe(s).");
                if (!meals.isEmpty()) results.getSelectionModel().selectFirst();
            }));
        });

        // Import the selected recipe and its ingredients
        importButton.setOnAction(event -> {
            MealRecipe selected =
                    results.getSelectionModel().getSelectedItem();

            if (selected == null) {
                return;
            }

            importButton.setDisable(true);
            status.setText(
                    "Importing recipe and ingredients to SQLite...");

            CompletableFuture.supplyAsync(() -> {
                try {
                    MealRecipe completeRecipe = selected.ingredients().isEmpty()
                            ? api.lookupById(selected.id()) : selected;
                    String itemType = "Starter".equalsIgnoreCase(completeRecipe.category())
                            ? "APPETIZER" : "MAIN_COURSE";
                    recipeRepository.importRecipe(completeRecipe, itemType);
                    return completeRecipe;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).whenComplete((importedRecipe, error) ->
                    Platform.runLater(() -> {
                        importButton.setDisable(false);

                        if (error != null) {
                            status.setText("Recipe import failed.");
                            output.setText(message(error));
                        } else {
                            status.setText(
                                    "Recipe imported. Set its selling price in Menu Management.");
                            output.setText(
                                    "Imported from TheMealDB:\n"
                                    + importedRecipe.name()
                                    + " (API meal ID " + importedRecipe.id() + ")\n\n"
                                    + "Category saved: " + ("Starter".equalsIgnoreCase(importedRecipe.category()) ? "APPETIZER" : "MAIN_COURSE") + "\n"
                                    + "Ingredients saved: " + importedRecipe.ingredients().size()
                                    + "\n\n" + formatRecipe(importedRecipe));
                            try {
                                refreshMenu(menuList);
                                refreshMenuChart(menuChart);
                            } catch (Exception ex) {
                                output.appendText("\nCould not refresh menu: " + message(ex));
                            }
                        }
                    }));
        });

        // Reusable layout rows
        HBox menuSearchRow = new HBox(
                10, menuSearchField, searchSavedMenuButton,
                showAllMenusButton, savedCategoryChoice, filterCategoryButton);
        HBox.setHgrow(menuSearchField, Priority.ALWAYS);

        HBox menuButtons = new HBox(10, refreshMenuButton, updatePriceButton, deleteMenuButton);
        HBox searchRow = new HBox(10, searchField, searchButton, apiCategoryChoice, browseCategoryButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox.setHgrow(beverageNameField, Priority.ALWAYS);
        HBox.setHgrow(comboNameField, Priority.ALWAYS);

        // Dashboard page
        Label dashboardIntro = new Label("Here's what's happening in your restaurant today.");
        dashboardIntro.getStyleClass().add("page-subtitle");
        FlowPane metricCards = new FlowPane(14, 14);
        metricCards.setPrefWrapLength(900);
        mainCourseMetric = metricValueCard("MAIN COURSES", "0", "Menu catalog");
        appetizerMetric = metricValueCard("APPETIZERS", "0", "Starters");
        beverageMetric = metricValueCard("BEVERAGES", "0", "Drinks");
        comboMetric = metricValueCard("COMBO MEALS", "0", "Meal bundles");
        metricCards.getChildren().addAll(
                metricCard("🍽", "MAIN COURSES", mainCourseMetric, "metric-green"),
                metricCard("✦", "APPETIZERS", appetizerMetric, "metric-orange"),
                metricCard("◉", "BEVERAGES", beverageMetric, "metric-blue"),
                metricCard("▣", "COMBO MEALS", comboMetric, "metric-purple"));

        VBox dashboardChartCard = contentCard(
                sectionTitle("Menu overview", "A live view of your saved menu mix"),
                menuChart, refreshChartButton);
        menuChart.setPrefHeight(330);
        VBox quickStartCard = contentCard(
                sectionTitle("Getting started", "A few useful actions for your team"),
                dbButton,
                new Label("Import recipes, maintain your menu prices, create combos, and monitor your menu insights from the navigation on the left."));
        output.setPrefRowCount(5);
        output.setMaxHeight(170);
        VBox activityCard = contentCard(
                sectionTitle("Activity log", "Database operations, imports, and system feedback"), output);
        VBox dashboardPage = page("Dashboard", "Restaurant overview", dashboardIntro,
                metricCards, dashboardChartCard, quickStartCard, activityCard);

        // Menu management page
        menuList.setPrefHeight(390);
        VBox menuToolsCard = contentCard(
                sectionTitle("Menu catalog", "Search, filter, and maintain your restaurant items"),
                menuSearchRow, menuList,
                new Label("SELLING PRICE"),
                new HBox(10, priceField, updatePriceButton),
                new HBox(10, refreshMenuButton, deleteMenuButton));
        HBox.setHgrow(priceField, Priority.ALWAYS);
        VBox menuPage = page("Menu management", "Manage all items available in your restaurant",
                menuToolsCard);

        // Beverage management page
        beverageList = new ListView<>();
        beverageList.setPrefHeight(340);
        Button refreshBeverageListButton = new Button("Refresh beverage list");
        refreshBeverageListButton.getStyleClass().add("button-secondary");
        refreshBeverageListButton.setOnAction(e -> {
            try { beverageList.getItems().setAll(menuRepository.findByTypeSummaries("BEVERAGE")); }
            catch (Exception ex) { status.setText("Could not load beverages."); output.setText(message(ex)); }
        });
        VBox beverageForm = contentCard(
                sectionTitle("Add a beverage", "Create drinks that are unique to your restaurant"),
                new HBox(10, beverageNameField, beveragePriceField, addBeverageButton),
                new Label("Examples: Mango Juice, Iced Tea, Lemonade, Bottled Water"));
        beveragePriceField.setMaxWidth(180);
        VBox beveragePage = page("Beverage management", "Maintain your drinks and their selling prices",
                beverageForm,
                contentCard(sectionTitle("Saved beverages", "Beverage items currently in your SQLite menu"),
                        refreshBeverageListButton, beverageList));

        // Combo management page
        VBox comboForm = contentCard(
                sectionTitle("Build a combo", "Bundle saved menu items and apply a discount"),
                new Label("COMBO NAME"), comboNameField,
                new Label("ITEMS AND QUANTITIES"), comboItemsField,
                new Label("Use the format: Item Name:Quantity, Item Name:Quantity"),
                new HBox(10, new VBox(5, new Label("DISCOUNT (%)"), comboDiscountField),
                        createComboButton, refreshCombosButton));
        comboList.setPrefHeight(350);
        VBox comboPage = page("Combo management", "Create value meals from your existing menu items",
                comboForm,
                contentCard(sectionTitle("Saved combos", "Your current restaurant bundles and calculated prices"), comboList));

        // Recipe discovery page
        results.setPrefHeight(230);
        recipeDetails.setPrefRowCount(8);
        VBox recipeSearchCard = contentCard(
                sectionTitle("Discover recipes", "Search TheMealDB and import food recipes into DineSmart"),
                searchRow, results,
                sectionTitle("Recipe details", "Review ingredients and preparation instructions"),
                recipeDetails, importButton);
        VBox recipePage = page("Recipe discovery", "Find main courses and appetizers for your menu",
                recipeSearchCard);

        // Orders & kitchen queue page.
        VBox createOrderCard = contentCard(
                sectionTitle("Create customer order", "Build an order from your saved main courses, appetizers, beverages, and combos"),
                new HBox(10, customerNameField, orderItemChoice, orderQuantityField, addToCartButton),
                orderCartList, new HBox(10, removeCartItemButton, clearCartButton, cartTotalLabel),
                placeOrderButton);
        HBox.setHgrow(customerNameField, Priority.ALWAYS);
        HBox.setHgrow(orderItemChoice, Priority.ALWAYS);
        VBox activeQueueCard = contentCard(
                sectionTitle("Kitchen order queue", "Pending and preparing orders appear here in arrival order"),
                orderQueueSummary, activeOrdersList,
                new HBox(10, refreshQueueButton, prepareOrderButton, completeOrderButton, cancelOrderButton),
                cancelLatestOrderButton);
        VBox orderHistoryCard = contentCard(
                sectionTitle("Recent order history", "Latest 100 orders, including completed and cancelled orders"), orderHistoryList);
        VBox ordersPage = page("Customer orders", "Create orders, manage the kitchen queue, and track order status",
                createOrderCard, activeQueueCard, orderHistoryCard);

        // Reports page
        CategoryAxis reportCategoryAxis = new CategoryAxis();
        reportCategoryAxis.setLabel("Menu category");
        NumberAxis reportCountAxis = new NumberAxis();
        reportCountAxis.setLabel("Saved items");
        BarChart<String, Number> reportChart = new BarChart<>(reportCategoryAxis, reportCountAxis);
        reportChart.setTitle("Menu category frequency");
        reportChart.setLegendVisible(false);
        reportChart.setPrefHeight(430);
        Button reportRefreshButton = new Button("Refresh report");
        reportRefreshButton.getStyleClass().add("button-secondary");
        reportRefreshButton.setOnAction(e -> {
            try { refreshMenuChart(reportChart); status.setText("Reports refreshed from SQLite."); }
            catch (Exception ex) { status.setText("Could not refresh reports."); output.setText(message(ex)); }
        });
        Button refreshSalesButton = new Button("Refresh sales charts");
        refreshSalesButton.getStyleClass().add("button-secondary");
        refreshSalesButton.setOnAction(e -> {
            try { refreshBestSellerChart(salesChart); refreshSalesSummary(todaySalesLabel, todayOrdersLabel); status.setText("Sales analytics refreshed from completed orders."); }
            catch (Exception ex) { status.setText("Could not refresh sales analytics."); output.setText(message(ex)); }
        });
        VBox salesSummaryCard = contentCard(sectionTitle("Today's sales", "Only completed orders are counted as sales"),
                new HBox(24, new VBox(5, new Label("NET ORDER SALES"), todaySalesLabel), new VBox(5, new Label("COMPLETED ORDERS"), todayOrdersLabel)), refreshSalesButton);
        todaySalesLabel.getStyleClass().add("sales-total");
        todayOrdersLabel.getStyleClass().add("sales-count");
        CategoryAxis hourAxis = new CategoryAxis();
        hourAxis.setLabel("Order hour");
        NumberAxis hourlyCountAxis = new NumberAxis();
        hourlyCountAxis.setLabel("Number of orders");
        LineChart<String, Number> hourlyOrdersChart = new LineChart<>(hourAxis, hourlyCountAxis);
        hourlyOrdersChart.setTitle("Orders by hour (peak ordering analysis)");
        hourlyOrdersChart.setCreateSymbols(true);
        hourlyOrdersChart.setAnimated(false);
        hourlyOrdersChart.setPrefHeight(330);
        Label peakHourLabel = new Label("Peak hour: Not calculated yet");
        peakHourLabel.getStyleClass().add("sales-count");
        Label historyAnalysisLabel = new Label("Historical order analysis has not run yet.");

Button analyzeHistoryButton =
        new Button("Analyze historical orders (background)");

analyzeHistoryButton.setOnAction(e -> {
    analyzeHistoryButton.setDisable(true);

    historyAnalysisLabel.setText(
            "Analyzing historical order log in background...");

    CompletableFuture.supplyAsync(() -> {
        try {
            return orderRepository.analyzeHistoricalOrderLog();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }).whenComplete((result, error) ->
            Platform.runLater(() -> {

                analyzeHistoryButton.setDisable(false);

                if (error != null) {
                    historyAnalysisLabel.setText(
                            "Historical analysis failed: "
                            + message(error));
                    return;
                }

                try {
                    // Display the latest historical analysis.
                    historyAnalysisLabel.setText(result);

                    // Refresh the best-selling chart using
                    // the same completed-order database query.
                    refreshBestSellerChart(salesChart);

                    // Refresh today's sales summary.
                    refreshSalesSummary(
                            todaySalesLabel,
                            todayOrdersLabel);

                    // Refresh hourly ordering statistics.
                    refreshHourlyOrdersChart(
                            hourlyOrdersChart,
                            peakHourLabel);

                    status.setText(
                            "Historical analysis and all sales charts updated successfully.");

                } catch (Exception ex) {
                    historyAnalysisLabel.setText(
                            result
                            + "\n\nChart refresh failed: "
                            + message(ex));
                }
            }));
});

        
        Button refreshHourlyChartButton = new Button("Refresh hourly chart");
        refreshHourlyChartButton.getStyleClass().add("button-secondary");
        refreshHourlyChartButton.setOnAction(e -> {
            try { refreshHourlyOrdersChart(hourlyOrdersChart, peakHourLabel); status.setText("Hourly ordering analysis refreshed."); }
            catch (Exception ex) { status.setText("Could not refresh hourly analysis."); output.setText(message(ex)); }
        });
        VBox reportsPage = page("Reports & insights", "Understand menu performance, sales, and peak ordering periods",
                salesSummaryCard,
                contentCard(sectionTitle("Best-selling foods & beverages", "Top items ranked by quantities in completed orders"), salesChart),
                contentCard(sectionTitle("Orders by hour", "Hourly order volume and busiest restaurant period"), peakHourLabel, hourlyOrdersChart, refreshHourlyChartButton, analyzeHistoryButton, historyAnalysisLabel),
                contentCard(sectionTitle("Menu category analysis", "Live SQL aggregation of saved menu items"),
                        new Label("This chart is generated from the current SQLite menu data."), reportRefreshButton, reportChart));

        // Sidebar navigation and application shell
        Label brandMark = new Label("D");
        brandMark.getStyleClass().add("brand-mark");
        Label brandName = new Label("DineSmart");
        brandName.getStyleClass().add("brand-name");
        Label brandCaption = new Label("RESTAURANT MANAGEMENT");
        brandCaption.getStyleClass().add("brand-caption");
        VBox brandBlock = new VBox(2, new HBox(10, brandMark, brandName), brandCaption);
        brandBlock.getStyleClass().add("brand-block");

        Label navLabel = new Label("WORKSPACE");
        navLabel.getStyleClass().add("nav-section-label");
        Button dashboardNav = navButton("⌂", "Dashboard");
        Button menuNav = navButton("▤", "Menu management");
        Button ordersNav = navButton("▣", "Customer orders");
        Button beverageNav = navButton("◉", "Beverages");
        Button comboNav = navButton("▣", "Combo meals");
        Button recipeNav = navButton("⌕", "Recipe discovery");
        Button reportsNav = navButton("▥", "Reports & insights");
        VBox navigation = new VBox(7, navLabel, dashboardNav, menuNav, ordersNav,
                beverageNav, comboNav, recipeNav, reportsNav);
        navigation.getStyleClass().add("navigation");

        Label sidebarFooter = new Label("DineSmart  |  CPE 314\nRestaurant Ordering & Inventory Insights");
        sidebarFooter.getStyleClass().add("sidebar-footer");
        Region sidebarSpacer = new Region();
        VBox.setVgrow(sidebarSpacer, Priority.ALWAYS);
        VBox sidebar = new VBox(25, brandBlock, navigation, sidebarSpacer, sidebarFooter);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(245);
        sidebar.setMinWidth(220);

        Label topTitle = new Label("Dashboard");
        topTitle.getStyleClass().add("topbar-title");
        Label topHint = new Label("Your restaurant at a glance");
        topHint.getStyleClass().add("topbar-hint");
        VBox topHeading = new VBox(3, topTitle, topHint);
        Label statusDot = new Label("●  SYSTEM READY");
        statusDot.getStyleClass().add("system-status");
        HBox topbar = new HBox(topHeading, statusDot);
        topbar.setAlignment(Pos.CENTER_LEFT);
        topbar.setSpacing(20);
        HBox.setHgrow(topHeading, Priority.ALWAYS);
        topbar.getStyleClass().add("topbar");

        StackPane pageHost = new StackPane();
        pageHost.getStyleClass().add("page-host");
        BorderPane shell = new BorderPane();
        shell.setLeft(sidebar);
        shell.setTop(topbar);
        shell.setCenter(pageHost);
        shell.getStyleClass().add("app-shell");

        java.util.Map<String, javafx.scene.Node> pages = new java.util.LinkedHashMap<>();
        pages.put("Dashboard", dashboardPage);
        pages.put("Menu management", menuPage);
        pages.put("Customer orders", ordersPage);
        pages.put("Beverages", beveragePage);
        pages.put("Combo meals", comboPage);
        pages.put("Recipe discovery", recipePage);
        pages.put("Reports & insights", reportsPage);
        java.util.Map<String, Button> navButtons = java.util.Map.of(
                "Dashboard", dashboardNav, "Menu management", menuNav,
                "Customer orders", ordersNav, "Beverages", beverageNav, "Combo meals", comboNav,
                "Recipe discovery", recipeNav, "Reports & insights", reportsNav);

        java.util.function.Consumer<String> showPage = pageName -> {
            pageHost.getChildren().setAll(pages.get(pageName));
            topTitle.setText(pageName);
            topHint.setText(switch (pageName) {
                case "Dashboard" -> "Your restaurant at a glance";
                case "Menu management" -> "Maintain your restaurant catalog and pricing";
                case "Customer orders" -> "Create customer orders and manage the kitchen queue";
                case "Beverages" -> "Manage drinks and beverage pricing";
                case "Combo meals" -> "Create bundles and value meals";
                case "Recipe discovery" -> "Discover food recipes with TheMealDB";
                default -> "Review menu data and category performance";
            });
            navButtons.forEach((name, button) -> {
                button.getStyleClass().remove("nav-button-active");
                if (name.equals(pageName)) button.getStyleClass().add("nav-button-active");
            });
        };
        dashboardNav.setOnAction(e -> showPage.accept("Dashboard"));
        menuNav.setOnAction(e -> showPage.accept("Menu management"));
        ordersNav.setOnAction(e -> { refreshOrderChoices.run(); refreshOrders.run(); showPage.accept("Customer orders"); });
        beverageNav.setOnAction(e -> {
            try { beverageList.getItems().setAll(menuRepository.findByTypeSummaries("BEVERAGE")); }
            catch (Exception ex) { status.setText("Could not load beverages."); output.setText(message(ex)); }
            showPage.accept("Beverages");
        });
        comboNav.setOnAction(e -> { refreshCombos.run(); showPage.accept("Combo meals"); });
        recipeNav.setOnAction(e -> showPage.accept("Recipe discovery"));
        reportsNav.setOnAction(e -> {
            try { refreshMenuChart(reportChart); }
            catch (Exception ex) { status.setText("Could not refresh reports."); output.setText(message(ex)); }
            showPage.accept("Reports & insights");
        });
        showPage.accept("Dashboard");

        // Compact application status strip
        HBox statusStrip = new HBox(status);
        statusStrip.getStyleClass().add("status-strip");
        shell.setBottom(statusStrip);

        stage.setTitle("DineSmart | Restaurant Management System");
        Scene applicationScene = new Scene(shell, 1440, 920);
        var stylesheet = getClass().getResource("/styles/dinesmart.css");
        if (stylesheet != null) applicationScene.getStylesheets().add(stylesheet.toExternalForm());

        // Welcome page is the first screen. The existing application shell and
        // every original feature remain available after the user selects Get Started.
        StackPane homeRoot = new StackPane();
        homeRoot.getStyleClass().add("home-root");
        Image foodImage = new Image(
                getClass().getResource("/images/dinesmart-food-hero.png").toExternalForm(),
                false);
        ImageView foodBackground = new ImageView(foodImage);
        foodBackground.setPreserveRatio(false);
        foodBackground.fitWidthProperty().bind(homeRoot.widthProperty());
        foodBackground.fitHeightProperty().bind(homeRoot.heightProperty());

        Rectangle softOverlay = new Rectangle();
        softOverlay.widthProperty().bind(homeRoot.widthProperty());
        softOverlay.heightProperty().bind(homeRoot.heightProperty());
        softOverlay.setFill(new LinearGradient(
                0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.97)),
                new Stop(0.48, Color.rgb(255, 255, 255, 0.88)),
                new Stop(0.78, Color.rgb(255, 255, 255, 0.25)),
                new Stop(1, Color.rgb(255, 255, 255, 0.08))));

        Label homeBrand = new Label("DineSmart");
        homeBrand.getStyleClass().add("home-brand");
        Label homeCaption = new Label("RESTAURANT ORDERING & INVENTORY INSIGHTS");
        homeCaption.getStyleClass().add("home-caption");
        VBox homeBrandBlock = new VBox(2, homeBrand, homeCaption);

        Label welcomeEyebrow = new Label("WELCOME TO YOUR SMARTER RESTAURANT");
        welcomeEyebrow.getStyleClass().add("home-eyebrow");
        Label welcomeTitle = new Label("Good Food.\nSmart Ordering.\nBetter Insights.");
        welcomeTitle.getStyleClass().add("home-title");
        welcomeTitle.setTextAlignment(TextAlignment.LEFT);
        welcomeTitle.setWrapText(true);
        Label welcomeDescription = new Label(
                "Manage your menu, organize customer orders, discover recipes, "
                + "and understand your restaurant operations — all in one place.");
        welcomeDescription.getStyleClass().add("home-description");
        welcomeDescription.setWrapText(true);
        welcomeDescription.setMaxWidth(590);

        HBox featureChips = new HBox(12,
                homeFeature("🛒", "Order Management"),
                homeFeature("▤", "Menu & Recipes"),
                homeFeature("▦", "Combo Meals"),
                homeFeature("▥", "Reports & Insights"));
        featureChips.getStyleClass().add("home-feature-row");

        Button getStartedButton = new Button("Get Started   →");
        getStartedButton.getStyleClass().add("home-cta");
        getStartedButton.setOnAction(event -> stage.setScene(applicationScene));

        VBox welcomeContent = new VBox(20, homeBrandBlock, welcomeEyebrow,
                welcomeTitle, welcomeDescription, featureChips, getStartedButton);
        welcomeContent.getStyleClass().add("home-content");
        welcomeContent.setMaxWidth(720);
        welcomeContent.setAlignment(Pos.CENTER_LEFT);

        Label homeFooter = new Label("DineSmart  •  Good Food. Smart Ordering. Better Insights.");
        homeFooter.getStyleClass().add("home-footer");
        BorderPane homeLayout = new BorderPane();
        homeLayout.setCenter(welcomeContent);
        homeLayout.setBottom(homeFooter);
        BorderPane.setMargin(welcomeContent, new Insets(55, 45, 30, 75));
        BorderPane.setMargin(homeFooter, new Insets(10, 30, 22, 75));
        homeRoot.getChildren().addAll(foodBackground, softOverlay, homeLayout);

        Scene homeScene = new Scene(homeRoot, 1440, 900);
        if (stylesheet != null) homeScene.getStylesheets().add(stylesheet.toExternalForm());
        stage.setMinWidth(1120);
        stage.setMinHeight(760);
        stage.setScene(homeScene);
        stage.show();

        // Load existing menu entries and refresh dashboard figures at startup.
        try {
            Database.initialize();
            menuRepository.deleteInvalidRows();
            refreshMenu(menuList);
            refreshMenuChart(menuChart);
            refreshMenuChart(reportChart);
            refreshCombos.run();
            beverageList.getItems().setAll(menuRepository.findByTypeSummaries("BEVERAGE"));
            updateMetric(mainCourseMetric, "MAIN_COURSE");
            updateMetric(appetizerMetric, "APPETIZER");
            updateMetric(beverageMetric, "BEVERAGE");
            comboMetric.setText(String.valueOf(comboRepository.findAllSummaries().size()));
            refreshOrderChoices.run(); refreshOrders.run();
            refreshBestSellerChart(salesChart); refreshSalesSummary(todaySalesLabel, todayOrdersLabel);
            refreshHourlyOrdersChart(hourlyOrdersChart, peakHourLabel);
        } catch (Exception ex) {
            status.setText("Menu not loaded yet. Use Initialize Database if needed.");
            output.setText(message(ex));
        }
    }

    private VBox page(String heading, String subtitle, javafx.scene.Node... content) {
        Label title = new Label(heading);
        title.getStyleClass().add("page-title");
        Label description = new Label(subtitle);
        description.getStyleClass().add("page-subtitle");
        VBox body = new VBox(18);
        body.getChildren().addAll(content);
        body.setPadding(new Insets(26, 32, 34, 32));
        body.getStyleClass().add("page-content");
        VBox wrapper = new VBox(8, title, description, body);
        wrapper.setPadding(new Insets(24, 28, 24, 28));
        wrapper.getStyleClass().add("page-wrapper");
        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        return new VBox(scroll);
    }

    private VBox contentCard(javafx.scene.Node... children) {
        VBox card = new VBox(14, children);
        card.setPadding(new Insets(20));
        card.getStyleClass().add("content-card");
        return card;
    }

    private VBox metricCard(String icon, String title, Label value, String accentClass) {
        Label glyph = new Label(icon);
        glyph.getStyleClass().add("metric-icon");
        Label heading = new Label(title);
        heading.getStyleClass().add("metric-heading");
        value.getStyleClass().add("metric-value");
        Label foot = new Label("LIVE DATABASE COUNT");
        foot.getStyleClass().add("metric-foot");
        VBox card = new VBox(10, new HBox(glyph, heading), value, foot);
        card.getStyleClass().addAll("metric-card", accentClass);
        card.setPrefWidth(205);
        card.setMinWidth(180);
        card.setPadding(new Insets(18));
        return card;
    }

    private Label metricValueCard(String title, String value, String foot) {
        Label label = new Label(value);
        label.setUserData(title + "|" + foot);
        return label;
    }

    private Button navButton(String icon, String text) {
        Label glyph = new Label(icon);
        glyph.getStyleClass().add("nav-icon");
        Label caption = new Label(text);
        caption.getStyleClass().add("nav-caption");
        Button button = new Button();
        button.setGraphic(new HBox(13, glyph, caption));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.getStyleClass().add("nav-button");
        return button;
    }

    private void updateMetric(Label metric, String itemType) throws Exception {
        int count = menuRepository.findByTypeSummaries(itemType).size();
        metric.setText(String.valueOf(count));
    }

    private Label sectionTitle(String heading, String subtitle) {
        Label label = new Label(heading + "\n" + subtitle);
        label.getStyleClass().add("section-title");
        return label;
    }

    private void refreshMenu(ListView<String> menuList)
            throws Exception {
        menuList.getItems().setAll(menuRepository.findAllPriceSummaries());
        refreshDashboardMetrics();
    }

    private void refreshDashboardMetrics() {
        try {
            if (mainCourseMetric != null) updateMetric(mainCourseMetric, "MAIN_COURSE");
            if (appetizerMetric != null) updateMetric(appetizerMetric, "APPETIZER");
            if (beverageMetric != null) updateMetric(beverageMetric, "BEVERAGE");
            if (comboMetric != null) comboMetric.setText(String.valueOf(comboRepository.findAllSummaries().size()));
        } catch (Exception ignored) {
            // Keep the main screen usable if a dashboard count cannot refresh.
        }
    }


    private void refreshMenuChart(BarChart<String, Number> chart)
            throws Exception {
        java.util.Map<String, Integer> minedCounts =
                menuRepository.countByItemType();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Saved menu items");
        for (java.util.Map.Entry<String, Integer> entry
                : minedCounts.entrySet()) {
            String readableCategory = entry.getKey().replace('_', ' ');
            series.getData().add(
                    new XYChart.Data<>(readableCategory, entry.getValue()));
        }

        chart.getData().clear();
        if (!series.getData().isEmpty()) {
            chart.getData().add(series);
        }
    }

    private void refreshBestSellerChart(BarChart<String, Number> chart) throws Exception {
        chart.getData().clear();
        XYChart.Series<String, Number> food = new XYChart.Series<>();
        food.setName("Food");
        XYChart.Series<String, Number> beverages = new XYChart.Series<>();
        beverages.setName("Beverages");
        for (OrderRepository.BestSeller item : orderRepository.findBestSellers(8)) {
            boolean drink = "BEVERAGE".equalsIgnoreCase(item.type());
            (drink ? beverages : food).getData().add(new XYChart.Data<>(item.name(), item.quantity()));
        }
        if (!food.getData().isEmpty()) chart.getData().add(food);
        if (!beverages.getData().isEmpty()) chart.getData().add(beverages);
    }

    private void refreshHourlyOrdersChart(LineChart<String, Number> chart, Label peakLabel) throws Exception {
        java.util.Map<Integer, Integer> hourly = orderRepository.getOrdersByHour();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Orders");
        for (java.util.Map.Entry<Integer, Integer> entry : hourly.entrySet()) {
            int hour = entry.getKey();
            String label = String.format("%02d:00", hour);
            series.getData().add(new XYChart.Data<>(label, entry.getValue()));
        }
        chart.getData().setAll(series);
        peakLabel.setText("Peak ordering hour: " + orderRepository.getPeakOrderingHour());
    }

    private void refreshSalesSummary(Label sales, Label orders) throws Exception {
        sales.setText("₱" + String.format("%,.2f", orderRepository.getTodaySales()));
        orders.setText(orderRepository.getTodayOrderCount() + " completed orders");
    }

    private static String formatRecipe(MealRecipe recipe) {
        StringBuilder b = new StringBuilder();

        b.append(recipe.name())
                .append("\nCategory: ")
                .append(blankAsDash(recipe.category()))
                .append(" | Area: ")
                .append(blankAsDash(recipe.area()))
                .append("\nAPI meal ID: ")
                .append(recipe.id())
                .append("\n\nIngredients:\n");

        if (recipe.ingredients().isEmpty()) {
            b.append("No ingredient list returned.\n");
        } else {
            for (MealRecipe.Ingredient ingredient
                    : recipe.ingredients()) {

                b.append("• ").append(ingredient.name());

                if (ingredient.measure() != null
                        && !ingredient.measure().isBlank()) {
                    b.append(" — ").append(ingredient.measure());
                }

                b.append("\n");
            }
        }

        b.append("\nInstructions:\n")
                .append(blankAsDash(recipe.instructions()));

        return b.toString();
    }

    private VBox homeFeature(String glyph, String label) {
        Label icon = new Label(glyph);
        icon.getStyleClass().add("home-feature-icon");
        Label text = new Label(label);
        text.getStyleClass().add("home-feature-label");
        VBox card = new VBox(7, icon, text);
        card.getStyleClass().add("home-feature-card");
        card.setAlignment(Pos.CENTER);
        card.setMinWidth(132);
        return card;
    }

    private static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String message(Throwable throwable) {
        Throwable cause = throwable;

        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        return cause.getClass().getSimpleName()
                + ": " + cause.getMessage();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
