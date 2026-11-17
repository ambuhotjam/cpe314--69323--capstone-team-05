
package ph.uc.cpe314.dinesmart.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import ph.uc.cpe314.dinesmart.data.Database;
import ph.uc.cpe314.dinesmart.data.AuthRepository;
import ph.uc.cpe314.dinesmart.data.IngredientRepository;
import ph.uc.cpe314.dinesmart.data.RecipeCatalogRepository;
import ph.uc.cpe314.dinesmart.data.MenuItemRepository;
import ph.uc.cpe314.dinesmart.data.OrderRepository;
import ph.uc.cpe314.dinesmart.data.OrderReportRepository;
import ph.uc.cpe314.dinesmart.data.MenuPeriodRepository;
import ph.uc.cpe314.dinesmart.data.ReservationRepository;
import ph.uc.cpe314.dinesmart.data.MenuRecommendationRepository;
import ph.uc.cpe314.dinesmart.controller.AuthController;
import ph.uc.cpe314.dinesmart.controller.OrderController;
import ph.uc.cpe314.dinesmart.controller.MenuController;
import ph.uc.cpe314.dinesmart.controller.InventoryController;
import ph.uc.cpe314.dinesmart.controller.ReservationController;
import ph.uc.cpe314.dinesmart.controller.ReportController;
import ph.uc.cpe314.dinesmart.model.Appetizer;
import ph.uc.cpe314.dinesmart.model.Beverage;
import ph.uc.cpe314.dinesmart.model.MainCourse;
import ph.uc.cpe314.dinesmart.model.MealRecipe;
import ph.uc.cpe314.dinesmart.model.MenuItem;
import ph.uc.cpe314.dinesmart.service.KitchenQueue;
import ph.uc.cpe314.dinesmart.service.PendingOrderStack;
import ph.uc.cpe314.dinesmart.service.PersistentKitchenQueue;
import ph.uc.cpe314.dinesmart.service.TheMealDbService;
import ph.uc.cpe314.dinesmart.service.StaffPermissions;
import ph.uc.cpe314.dinesmart.service.MenuPhotoStorage;
import ph.uc.cpe314.dinesmart.util.Money;

import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DineSmartApp extends Application {

    private final TheMealDbService api = new TheMealDbService();
    private final AuthRepository authRepository = new AuthRepository();
    private final IngredientRepository ingredientRepository = new IngredientRepository();
    private final MenuRecommendationRepository recommendationRepository = new MenuRecommendationRepository();

    private final RecipeCatalogRepository recipeCatalogRepository =
            new RecipeCatalogRepository();

    private final MenuItemRepository menuRepository =
            new MenuItemRepository();
    private final OrderRepository orderRepository = new OrderRepository();
    private final OrderReportRepository orderReportRepository = new OrderReportRepository();
    private final ReservationRepository reservationRepository = new ReservationRepository();
    private final MenuController menuController = new MenuController(menuRepository);
    private final InventoryController inventoryController =
            new InventoryController(ingredientRepository);
    private final ReservationController reservationController =
            new ReservationController(reservationRepository);
    private final ReportController reportController =
            new ReportController(orderReportRepository, menuRepository);
    private final AuthController authController = new AuthController(authRepository);
    private final OrderController orderController = new OrderController(orderRepository);
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "dinesmart-io");
        thread.setDaemon(true);
        return thread;
    });
    private Label mainCourseMetric;
    private Label appetizerMetric;
    private Label beverageMetric;
    private ListView<String> beverageList;
    private FlowPane menuPhotoGallery;
    private final Set<Long> mealPhotoLookupsAttempted = ConcurrentHashMap.newKeySet();
    private final Set<Long> mealPhotoLookupsInProgress = ConcurrentHashMap.newKeySet();
    private final Map<Long, String> mealPhotoLookupMessages = new ConcurrentHashMap<>();
    // Shared across menu-save and reports handlers so both can refresh the same chart.
    private BarChart<String, Number> reportChart;

    // Shared state used by the start() handlers and helper methods below.
    private final String[] activeRole = {""};
    private Label status;
    private TextArea output;

    @Override
    public void start(Stage stage) throws Exception {
        final String[] activeDisplayName = {""};
        final Long[] activeStaffUserId = {null};

        Label title = new Label(
                "DineSmart — Restaurant Ordering & Inventory Insights");
        title.getStyleClass().add("app-title");

        status = new Label(
                "Search recipes, import them, and manage restaurant prices.");
        status.getStyleClass().add("status-banner");

        output = new TextArea();
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
            } catch (SQLException | RuntimeException ex) {
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
        menuPhotoGallery = new FlowPane(14, 14);
        menuPhotoGallery.setPrefWrapLength(1040);
        menuPhotoGallery.setMaxWidth(Double.MAX_VALUE);

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
                if ("All Categories".equals(choice)) {
                    if ("WAITER".equals(activeRole[0])) { menuList.getItems().setAll(menuRepository.findAvailableSummaries()); }
                    else refreshMenu(menuList);
                } else if ("WAITER".equals(activeRole[0])) menuList.getItems().setAll(menuRepository.findAvailableByType(choice.toUpperCase().replace(' ', '_')));
                else menuList.getItems().setAll(menuRepository.findByTypeSummaries(choice.toUpperCase().replace(' ', '_')));
                status.setText("Showing " + choice + " menu items.");
            } catch (SQLException | RuntimeException ex) { status.setText("Could not filter menu category."); output.setText(message(ex)); }
        });

        TextField priceField = new TextField();
        priceField.setPromptText("Enter selling price, e.g. 199.00");

        Button refreshMenuButton = new Button("Refresh Menu");
        Button updatePriceButton = new Button("Set / Update Price");
        Button deleteMenuButton = new Button("Delete Selected Menu");
        Button soldOutButton = new Button("Toggle SOLD OUT");
        soldOutButton.getStyleClass().add("button-secondary");
        Button dailyMenuButton = new Button("Add / Remove Today's Menu");
        dailyMenuButton.getStyleClass().add("button-secondary");
        deleteMenuButton.getStyleClass().add("button-danger");
        showAllMenusButton.getStyleClass().add("button-secondary");
        refreshMenuButton.getStyleClass().add("button-secondary");
        refreshChartButton.getStyleClass().add("button-secondary");

        // New menu items can include a food photo. The photo is copied to a
        // persistent local folder and its path is stored in SQLite.
        TextField newMenuNameField = new TextField();
        newMenuNameField.setPromptText("Dish name, e.g. Grilled Salmon");
        ComboBox<String> newMenuCategoryChoice = new ComboBox<>();
        newMenuCategoryChoice.getItems().addAll("Main Course", "Appetizer", "Beverage");
        newMenuCategoryChoice.setValue("Main Course");
        newMenuCategoryChoice.setPrefWidth(175);
        TextField newMenuPriceField = new TextField();
        newMenuPriceField.setPromptText("Selling price (₱)");
        newMenuPriceField.setPrefWidth(145);
        TextField newMenuPrepField = new TextField("15");
        newMenuPrepField.setPromptText("Prep time (minutes)");
        newMenuPrepField.setPrefWidth(155);
        File[] selectedMenuPhoto = {null};
        ImageView newMenuPhotoPreview = new ImageView();
        newMenuPhotoPreview.setFitWidth(112);
        newMenuPhotoPreview.setFitHeight(82);
        newMenuPhotoPreview.setPreserveRatio(false);
        newMenuPhotoPreview.setSmooth(true);
        Label selectedMenuPhotoLabel = new Label("No photo selected — DineSmart will look for an exact TheMealDB dish photo.");
        selectedMenuPhotoLabel.getStyleClass().add("menu-photo-hint");
        Button chooseMenuPhotoButton = new Button("Choose Dish Photo");
        chooseMenuPhotoButton.getStyleClass().add("button-secondary");
        Button saveMenuItemButton = new Button("Save Menu Item");
        Button clearMenuItemFormButton = new Button("Clear Form");
        clearMenuItemFormButton.getStyleClass().add("button-secondary");

        // Beverage items are maintained directly by the restaurant in SQLite.
        TextField beverageNameField = new TextField();
        beverageNameField.setPromptText("Beverage name, e.g. Mango Juice");
        TextField beveragePriceField = new TextField();
        beveragePriceField.setPromptText("Selling price");
        Button addBeverageButton = new Button("Add Beverage");
        addBeverageButton.setOnAction(event -> {
            try {
                String name = beverageNameField.getText().trim();
                long priceCents = Money.parseCents(beveragePriceField.getText());
                menuController.create(activeRole[0], new Beverage(name, priceCents, 2));
                beverageNameField.clear(); beveragePriceField.clear();
                refreshMenu(menuList); refreshMenuChart(menuChart);
                if (beverageList != null) { beverageList.getItems().setAll(menuRepository.findByTypeSummaries("BEVERAGE")); }
                status.setText("Beverage added to SQLite: " + name);
                output.setText("Saved beverage: " + name + " | ₱" + Money.formatCents(priceCents));
            } catch (NumberFormatException ex) {
                status.setText("Enter a valid beverage price.");
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not add beverage."); output.setText(message(ex));
            }
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
        TextField orderTableField = new TextField();
        orderTableField.setPromptText("Table label (optional)");
        orderTableField.setPrefWidth(150);
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
        ListView<String> readyToServeList = new ListView<>();
        readyToServeList.setPrefHeight(190);
        Button refreshReadyToServeButton = new Button("Refresh ready orders");
        refreshReadyToServeButton.getStyleClass().add("button-secondary");
        Button markSelectedServedButton = new Button("Mark selected as SERVED");
        PendingOrderStack pendingOrderStack = new PendingOrderStack();
        PersistentKitchenQueue persistentKitchenQueue = new PersistentKitchenQueue();
        ListView<String> orderHistoryList = new ListView<>();
        orderHistoryList.setPrefHeight(180);
        Label orderQueueSummary = new Label("Orders are saved to the local SQLite database.");
        orderQueueSummary.getStyleClass().add("page-subtitle");

        Runnable refreshReadyToServe = () -> {
            try {
                readyToServeList.getItems().setAll(orderRepository.findOrdersReadyToServe());
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not refresh orders ready to serve.");
                output.setText(message(ex));
            }
        };
        Runnable refreshOrders = () -> {
            try {
                activeOrdersList.getItems().setAll("KITCHEN".equals(activeRole[0]) ? orderRepository.findKitchenQueue() : orderRepository.findActiveQueue());
                java.util.List<String> pendingIds = orderRepository.findPendingOrderIdsOldestFirst();
                pendingOrderStack.rebuild(pendingIds);
                persistentKitchenQueue.rebuild(pendingIds);
                orderHistoryList.getItems().setAll(orderRepository.findOrderHistory());
                orderQueueSummary.setText(activeOrdersList.getItems().size() + " active order(s) in the kitchen queue · FIFO next: " + persistentKitchenQueue.nextOrderId().orElse("none"));
                refreshReadyToServe.run();
            } catch (SQLException | RuntimeException ex) { status.setText("Could not refresh orders."); output.setText(message(ex)); }
        };
        Runnable refreshOrderChoices = () -> {
            try { orderItemChoice.getItems().setAll(orderRepository.findOrderableItems()); }
            catch (SQLException | RuntimeException ex) { status.setText("Could not load menu items for ordering."); output.setText(message(ex)); }
        };

        soldOutButton.setOnAction(e -> {
            String selected = menuList.getSelectionModel().getSelectedItem();
            if (selected == null) { status.setText("Select a menu item first."); return; }
            String name = selected.split(" \\| ", 2)[0];
            try { menuController.toggleSoldOut(activeRole[0], name); refreshMenu(menuList); refreshOrderChoices.run(); status.setText(name + " availability updated."); }
            catch (SQLException | RuntimeException ex) { status.setText("Could not update sold-out status."); output.setText(message(ex)); }
        });
        dailyMenuButton.setOnAction(e -> {
            String selected = menuList.getSelectionModel().getSelectedItem();
            if (selected == null) { status.setText("Select a menu item first."); return; }
            String name = selected.split(" \\| ", 2)[0];
            boolean addToToday = selected.contains("NOT ON TODAY'S MENU");
            try {
                menuController.setDailyAvailability(activeRole[0], name, addToToday);
                refreshMenu(menuList);
                refreshOrderChoices.run();
                status.setText(addToToday ? name + " added to today's menu." : name + " removed from today's menu.");
                output.setText(addToToday
                        ? name + " is now available for today's service (unless marked SOLD OUT)."
                        : name + " is no longer available for today's service. The saved menu item was not deleted.");
            } catch (SQLException | RuntimeException ex) { status.setText("Could not update today's menu."); output.setText(message(ex)); }
        });
        Runnable updateCartDisplay = () -> {
            orderCartList.getItems().clear();
            long totalCents = 0L;
            for (java.util.Map.Entry<String, Integer> line : orderCart.entrySet()) {
                OrderRepository.MenuChoice choice = orderItemChoice.getItems().stream().filter(i -> i.cartKey().equals(line.getKey())).findFirst().orElse(null);
                long priceCents = choice == null ? 0L : choice.priceCents();
                String displayName = choice == null ? line.getKey() : choice.name();
                totalCents = Math.addExact(totalCents, Math.multiplyExact(priceCents, line.getValue().longValue()));
                orderCartList.getItems().add(displayName + "  ×  " + line.getValue() + "  |  ₱" + Money.formatCents(Math.multiplyExact(priceCents, line.getValue().longValue())));
            }
            cartTotalLabel.setText("Order total: ₱" + Money.formatCents(totalCents));
        };
        addToCartButton.setOnAction(e -> {
            OrderRepository.MenuChoice choice = orderItemChoice.getValue();
            if (choice == null) { status.setText("Choose a menu item first."); return; }
            try {
                int quantity = Integer.parseInt(orderQuantityField.getText().trim());
                if (quantity < 1) { throw new NumberFormatException(); }
                orderCart.merge(choice.cartKey(), quantity, Integer::sum);
                updateCartDisplay.run();
                status.setText(choice.name() + " added to the customer order.");
            } catch (NumberFormatException ex) { status.setText("Enter a whole-number quantity of at least 1."); }
        });
        removeCartItemButton.setOnAction(e -> {
            String selected = orderCartList.getSelectionModel().getSelectedItem();
            if (selected == null) { return; }
            String name = selected.substring(0, selected.lastIndexOf("  ×  "));
            String key = orderCart.keySet().stream().filter(k -> k.equals(name)).findFirst().orElse(name);
            orderCart.remove(key); updateCartDisplay.run();
        });
        clearCartButton.setOnAction(e -> { orderCart.clear(); updateCartDisplay.run(); });
        placeOrderButton.setOnAction(e -> {
            try {
                if (customerNameField.getText() == null || customerNameField.getText().isBlank()) {
                    throw new IllegalArgumentException("Enter the customer's name before placing the order.");
                }
                if (orderCart.isEmpty()) {
                    throw new IllegalArgumentException("Add at least one dish to the order first.");
                }

                String customer = customerNameField.getText().trim();
                String orderSummary = orderCartList.getItems().isEmpty()
                        ? "Order items saved." : String.join("\n", orderCartList.getItems());
                String totalSummary = cartTotalLabel.getText();
                String orderId = orderController.placeOrder(activeRole[0], activeStaffUserId[0], customer,
                        orderTableField.getText(), new java.util.LinkedHashMap<>(orderCart));

                // Refresh immediately after the database transaction commits.
                refreshOrders.run();
                boolean visibleInQueue = activeOrdersList.getItems().stream()
                        .anyMatch(row -> row.startsWith(orderId + "  |") || row.startsWith(orderId + " |"));

                orderCart.clear();
                customerNameField.clear();
                orderTableField.clear();
                updateCartDisplay.run();

                status.setText(visibleInQueue
                        ? "Order " + orderId + " added to the kitchen queue."
                        : "Order " + orderId + " was saved; refresh the kitchen queue to view it.");
                output.setText("New customer order saved.\nOrder number: " + orderId
                        + "\nCustomer: " + customer + "\nStatus: PENDING\n" + totalSummary
                        + "\n\nInventory: measurable ingredient requirements were recursively "
                        + "combined across all ordered dishes, checked against stock, and deducted "
                        + "within the same database transaction. Descriptive measures such as "
                        + "'salt to taste' are not assigned guessed quantities.");

                Alert placed = new Alert(visibleInQueue ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
                placed.setTitle(visibleInQueue ? "Order placed successfully" : "Order saved — queue refresh needed");
                placed.setHeaderText(visibleInQueue
                        ? "Order added to the kitchen queue"
                        : "The order was saved, but is not currently visible in the queue");
                placed.setContentText("Order number: " + orderId + "\nCustomer: " + customer
                        + "\nStatus: PENDING\n" + totalSummary + "\n\n" + orderSummary
                        + (visibleInQueue ? "\n\nKitchen staff can now start preparing this order."
                                : "\n\nUse Refresh queue. If it remains missing, check the order history and database.") );
                placed.showAndWait();
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not place order.");
                output.setText(message(ex));
                Alert failed = new Alert(Alert.AlertType.ERROR);
                failed.setTitle("Order could not be placed");
                failed.setHeaderText("DineSmart could not add this order to the kitchen queue");
                failed.setContentText(message(ex));
                failed.showAndWait();
            }
        });
        refreshQueueButton.setOnAction(e -> { refreshOrders.run(); refreshOrderChoices.run(); });
        java.util.function.BiConsumer<String, String> changeSelectedOrder = (newStatus, actionLabel) -> {
            String selected = activeOrdersList.getSelectionModel().getSelectedItem();
            if (selected == null) { status.setText("Select an order from the active queue first."); return; }
            String orderId = selected.split("\\s+\\|\\s+")[0].trim();
            Runnable apply = () -> {
                try {
                    orderController.changeStatus(activeRole[0], orderId, newStatus);
                    refreshOrders.run(); refreshBestSellerChart(salesChart); refreshSalesSummary(todaySalesLabel, todayOrdersLabel);
                    status.setText("Order " + orderId + " marked " + newStatus + ".");
                } catch (SQLException | RuntimeException ex) { status.setText("Could not " + actionLabel.toLowerCase() + " this order."); output.setText(message(ex)); }
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
        markSelectedServedButton.setOnAction(e -> {
            String selected = readyToServeList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Select an order from the Ready to serve list first.");
                return;
            }
            String orderId = selected.split("\\s+\\|\\s+")[0].trim();
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Confirm that order " + orderId + " has been delivered to the customer?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Confirm order served");
            confirmation.setHeaderText("Mark this order as SERVED?");
            confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
                try {
                    orderController.markServed(activeRole[0], orderId);
                    refreshOrders.run();
                    status.setText("Order " + orderId + " marked as SERVED.");
                } catch (SQLException | RuntimeException ex) {
                    status.setText("Could not mark this order as served.");
                    output.setText(message(ex));
                    refreshOrders.run();
                }
            });
        });
        refreshReadyToServeButton.setOnAction(e -> refreshReadyToServe.run());
        cancelLatestOrderButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Cancel the most recently placed pending order?", ButtonType.CANCEL, ButtonType.OK);
            confirm.setTitle("Cancel latest pending order");
            confirm.setHeaderText("Stack-based rush cancellation");
            confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
                try {
                    java.util.Optional<String> latest = pendingOrderStack.popMostRecent();
                    if (latest.isEmpty()) { status.setText("There are no pending orders to cancel."); }
                    else {
                        orderController.cancelPendingOrder(activeRole[0], latest.get());
                        refreshOrders.run();
                        status.setText("Most recent pending order cancelled using LIFO Stack: " + latest.get());
                    }
                } catch (SQLException | RuntimeException ex) { status.setText("Could not cancel latest pending order."); output.setText(message(ex)); }
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
                long priceCents = Money.parseCents(priceText);

                if (priceCents <= 0) {
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

                menuController.updatePrice(activeRole[0], itemName, priceCents);
                refreshMenu(menuList);

                status.setText("Price updated for " + itemName
                        + " to ₱" + Money.formatCents(priceCents));

                output.setText(
                        "Price saved successfully.\n"
                        + "Menu item: " + itemName + "\n"
                        + "New selling price: ₱"
                        + Money.formatCents(priceCents));

            } catch (NumberFormatException ex) {
                status.setText(
                        "Please enter a valid number, e.g. 199.00.");
            } catch (SQLException | RuntimeException ex) {
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
                mealPhotoLookupsAttempted.clear();
                mealPhotoLookupMessages.clear();
                refreshMenu(menuList);
                status.setText("Menu refreshed from SQLite.");
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not load the menu.");
                output.setText(message(ex));
            }
        });

        // Search saved menu items in SQLite
        searchSavedMenuButton.setOnAction(event -> {
            String query = menuSearchField.getText();

            try {
                if (query == null || query.isBlank()) {
                    if ("WAITER".equals(activeRole[0])) { menuList.getItems().setAll(menuRepository.findAvailableSummaries()); }
                    else refreshMenu(menuList);
                    status.setText("Showing menu items available for this role.");
                } else {
                    menuList.getItems().setAll("WAITER".equals(activeRole[0])
                            ? menuRepository.searchAvailableSummaries(query)
                            : menuRepository.searchPriceSummaries(query));

                    status.setText(
                            "Search completed. Matching items: "
                                    + menuList.getItems().size()
                    );

                    if (menuList.getItems().isEmpty()) {
                        output.setText(
                                "No saved menu items matched: " + query);
                    }
                }
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not search saved menus.");
                output.setText(message(ex));
            }
        });

        // Show the complete saved menu again
        showAllMenusButton.setOnAction(event -> {
            menuSearchField.clear();

            try {
                if ("WAITER".equals(activeRole[0])) { menuList.getItems().setAll(menuRepository.findAvailableSummaries()); }
                else refreshMenu(menuList);
                status.setText("Showing available menu items.");
            } catch (SQLException | RuntimeException ex) {
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
                    "If this dish has no order history, it will be permanently deleted. "
                    + "If it appears in past orders, it will be archived instead so receipts and reports remain intact.");

            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        boolean deleted =
                                menuController.delete(activeRole[0], itemName);

                        if (deleted) {
                            refreshMenu(menuList);
                            refreshMenuChart(menuChart);
                            status.setText(
                                    "Removed menu item from the active menu: " + itemName);
                            output.setText(
                                    "The item was permanently deleted if unused, or archived if it is referenced by historical orders.\n" + itemName);
                        } else {
                            status.setText(
                                    "Menu item was not found.");
                        }
                    } catch (SQLException | RuntimeException ex) {
                        status.setText(
                                "Could not delete the item. It may be "
                                + "referenced by an existing order.");
                        output.setText(message(ex));
                    }
                }
            });
        });

        // Initialize the schema only. A fresh installation starts with an empty restaurant catalog.
        dbButton.setOnAction(event -> {
            try {
                Database.initialize();
                refreshMenu(menuList);
                refreshMenuChart(menuChart);
                refreshDashboardMetrics();
                output.setText("DineSmart database is ready. Your restaurant catalog is empty and ready for your own recipes, ingredients, and menu items.");
                status.setText("Database initialized. No sample menu items were added.");
            } catch (SQLException | RuntimeException ex) {
                status.setText("Database initialization failed.");
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
                    setGraphic(null);
                    setStyle("");
                    return;
                }

                ImageView thumbnail = new ImageView();
                thumbnail.setFitWidth(82);
                thumbnail.setFitHeight(66);
                thumbnail.setPreserveRatio(false);
                thumbnail.setSmooth(true);
                if (recipe.thumbnailUrl() != null && !recipe.thumbnailUrl().isBlank()) {
                    try {
                        thumbnail.setImage(new Image(recipe.thumbnailUrl(), 82, 66, false, true, true));
                    } catch (IllegalArgumentException imageError) {
                        thumbnail.setImage(null);
                    }
                }
                Rectangle clip = new Rectangle(82, 66);
                clip.setArcWidth(12);
                clip.setArcHeight(12);
                thumbnail.setClip(clip);

                Label name = new Label(recipe.name());
                name.getStyleClass().add("recipe-result-name");
                Label details = new Label(blankAsDash(recipe.category()) + "  ·  " + blankAsDash(recipe.area()));
                details.getStyleClass().add("recipe-result-meta");
                VBox text = new VBox(5, name, details);
                text.setAlignment(Pos.CENTER_LEFT);
                HBox row = new HBox(13, thumbnail, text);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 8, 6, 8));
                setGraphic(row);
                setText(null);
            }
        });

        TextArea recipeDetails = new TextArea();
        recipeDetails.setEditable(false);
        recipeDetails.setWrapText(true);
        recipeDetails.setPrefRowCount(6);

        Button importButton = new Button(
                "Save Recipe to Library & Make Available for Scheduling");
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
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("The recipe search was interrupted.", ex);
                }
            }, ioExecutor).whenComplete((meals, error) ->
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

        // Browse DineSmart categories. The service maps them to valid TheMealDB categories.
        browseCategoryButton.setOnAction(event -> {
            String category = "Appetizer (Starter)".equals(apiCategoryChoice.getValue())
                    ? "Appetizer" : "Main Course";
            browseCategoryButton.setDisable(true);
            results.getItems().clear();
            recipeDetails.clear();
            importButton.setDisable(true);
            status.setText("Loading TheMealDB " + category + " recipes...");
            CompletableFuture.supplyAsync(() -> {
                try {
                    return api.searchByCategory(category);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("The category search was interrupted.", ex);
                }
            }, ioExecutor).whenComplete((meals, error) -> Platform.runLater(() -> {
                browseCategoryButton.setDisable(false);
                if (error != null) {
                    status.setText("Could not browse " + category + " recipes.");
                    output.setText(message(error));
                    return;
                }
                results.getItems().setAll(meals);
                status.setText(meals.isEmpty()
                        ? "No " + category + " recipes were returned. Try again or search by name."
                        : "Found " + meals.size() + " " + category + " recipe(s).");
                if (!meals.isEmpty()) {
                    results.getSelectionModel().selectFirst();
                }
            }));
        });

        // The save action is wired after the Recipe Library and ingredient-review controls are declared.

        // Reusable layout rows
        HBox menuSearchRow = new HBox(
                10, menuSearchField, searchSavedMenuButton,
                showAllMenusButton, savedCategoryChoice, filterCategoryButton);
        HBox.setHgrow(menuSearchField, Priority.ALWAYS);

        HBox menuButtons = new HBox(10, refreshMenuButton, updatePriceButton, deleteMenuButton, soldOutButton, dailyMenuButton);
        HBox searchRow = new HBox(10, searchField, searchButton, apiCategoryChoice, browseCategoryButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox.setHgrow(beverageNameField, Priority.ALWAYS);

        // Dashboard page
        Label dashboardIntro = new Label("Here's what's happening in your restaurant today.");
        dashboardIntro.getStyleClass().add("page-subtitle");
        FlowPane metricCards = new FlowPane(14, 14);
        metricCards.setPrefWrapLength(900);
        mainCourseMetric = metricValueCard("MAIN COURSES", "0", "Menu catalog");
        appetizerMetric = metricValueCard("APPETIZERS", "0", "Starters");
        beverageMetric = metricValueCard("BEVERAGES", "0", "Drinks");
        metricCards.getChildren().addAll(
                metricCard("🍽", "MAIN COURSES", mainCourseMetric, "metric-green"),
                metricCard("✦", "APPETIZERS", appetizerMetric, "metric-orange"),
                metricCard("◉", "BEVERAGES", beverageMetric, "metric-blue"));

        VBox dashboardChartCard = contentCard(
                sectionTitle("Menu overview", "A live view of your saved menu mix"),
                menuChart, refreshChartButton);
        menuChart.setPrefHeight(330);
        Button resetRestaurantButton = new Button("Reset Restaurant Data");
        resetRestaurantButton.getStyleClass().add("button-secondary");
        VBox quickStartCard = contentCard(
                sectionTitle("Getting started", "A few useful actions for your team"),
                dbButton, resetRestaurantButton,
                new Label("A new installation starts empty. Add your own ingredients, recipes, menu items, and schedules. Reset Restaurant Data clears business records but keeps staff login accounts."));
        output.setPrefRowCount(5);
        output.setMaxHeight(170);
        VBox activityCard = contentCard(
                sectionTitle("Activity log", "Database operations, imports, and system feedback"), output);

        Button menuFeatureCard = photoFeatureCard("hero-menu.jpg", "Menu", "Manage food items and prices");
        Button ordersFeatureCard = photoFeatureCard("hero-kitchen.jpg", "Orders", "View and process customer orders");
        Button inventoryFeatureCard = photoFeatureCard("hero-inventory.jpg", "Inventory", "Track ingredients and stock");
        Button reportsFeatureCard = photoFeatureCard("hero-dining.jpg", "Reports", "Explore restaurant insights");
        FlowPane dashboardFeatureCards = new FlowPane(14, 14,
                menuFeatureCard, ordersFeatureCard, inventoryFeatureCard, reportsFeatureCard);
        dashboardFeatureCards.setPrefWrapLength(1000);
        VBox dashboardQuickAccess = contentCard(
                sectionTitle("Your workspace", "Jump straight into the tools your restaurant uses every day"),
                dashboardFeatureCards);

        VBox dashboardPage = page("Dashboard", "Restaurant overview",
                dashboardQuickAccess, metricCards, dashboardChartCard, quickStartCard, activityCard);

        // Menu management: supervisors/admins can add menu items with persistent photos.
        menuList.setPrefHeight(330);
        Button addMenuItemPhotoButton = chooseMenuPhotoButton;
        addMenuItemPhotoButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a photo for this menu item");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Food photos (JPG, JPEG, PNG)", "*.jpg", "*.jpeg", "*.png"));
            File selected = chooser.showOpenDialog(stage);
            if (selected == null) { return; }
            try {
                long maxBytes = 12L * 1024L * 1024L;
                if (Files.size(selected.toPath()) > maxBytes) {
                    status.setText("Please choose a photo smaller than 12 MB.");
                    return;
                }
                Image preview = new Image(selected.toURI().toString(), 480, 340, false, true, false);
                if (preview.isError()) { throw new IOException("The selected photo could not be opened."); }
                selectedMenuPhoto[0] = selected;
                newMenuPhotoPreview.setImage(preview);
                selectedMenuPhotoLabel.setText(selected.getName());
                status.setText("Dish photo selected. Save the menu item to keep this photo.");
            } catch (IOException ex) {
                status.setText("Could not read or preview this photo: " + message(ex));
                output.setText(message(ex));
            } catch (RuntimeException ex) {
                status.setText("Could not preview this photo: " + message(ex));
                output.setText(message(ex));
            }
        });

        HBox menuItemDetailsRow = new HBox(10, newMenuNameField, newMenuCategoryChoice,
                newMenuPriceField, newMenuPrepField);
        HBox.setHgrow(newMenuNameField, Priority.ALWAYS);
        HBox photoControls = new HBox(14, newMenuPhotoPreview,
                new VBox(8, selectedMenuPhotoLabel, chooseMenuPhotoButton));
        photoControls.setAlignment(Pos.CENTER_LEFT);
        HBox saveMenuActions = new HBox(10, saveMenuItemButton, clearMenuItemFormButton);
        VBox menuItemFormCard = contentCard(
                sectionTitle("Add a menu item", "Save a dish with its category, selling price, preparation time, and photo"),
                menuItemDetailsRow, photoControls, saveMenuActions);
        menuItemFormCard.setVisible(false);
        menuItemFormCard.setManaged(false);

        clearMenuItemFormButton.setOnAction(event -> {
            newMenuNameField.clear();
            newMenuCategoryChoice.setValue("Main Course");
            newMenuPriceField.clear();
            newMenuPrepField.setText("15");
            selectedMenuPhoto[0] = null;
            newMenuPhotoPreview.setImage(null);
            selectedMenuPhotoLabel.setText("No photo selected — DineSmart will look for an exact TheMealDB dish photo.");
            status.setText("Menu item form cleared.");
        });

        saveMenuItemButton.setOnAction(event -> {
            String itemName = newMenuNameField.getText() == null ? "" : newMenuNameField.getText().trim();
            String category = newMenuCategoryChoice.getValue();
            try {
                if (itemName.isBlank()) {
                    status.setText("Enter a dish name before saving.");
                    newMenuNameField.requestFocus();
                    return;
                }
                if (category == null) {
                    status.setText("Choose a menu category.");
                    return;
                }
                long priceCents = Money.parseCents(newMenuPriceField.getText());
                int prepMinutes = Integer.parseInt(newMenuPrepField.getText().trim());
                if (priceCents <= 0) {
                    status.setText("Enter a selling price greater than ₱0.00.");
                    return;
                }
                if (prepMinutes < 0 || prepMinutes > 360) {
                    status.setText("Preparation time must be between 0 and 360 minutes.");
                    return;
                }

                ph.uc.cpe314.dinesmart.model.MenuItem newItem = switch (category) {
                    case "Appetizer" -> new Appetizer(itemName, priceCents, prepMinutes);
                    case "Beverage" -> new Beverage(itemName, priceCents, prepMinutes);
                    default -> new MainCourse(itemName, priceCents, prepMinutes);
                };
                String savedPhotoPath = selectedMenuPhoto[0] == null ? null
                        : saveMenuItemPhoto(selectedMenuPhoto[0], itemName);
                menuController.create(activeRole[0], newItem, savedPhotoPath, null);
                refreshMenu(menuList);
                refreshMenuChart(menuChart);
                refreshMenuChart(reportChart);
                refreshOrderChoices.run();
                if (beverageList != null) { beverageList.getItems().setAll(menuRepository.findByTypeSummaries("BEVERAGE")); }
                status.setText("Saved " + itemName + " to the menu" + (savedPhotoPath == null
                        ? ". Looking for an exact matching photo on TheMealDB…"
                        : " with your selected photo."));
                output.setText("Menu item saved successfully.\nName: " + itemName
                        + "\nCategory: " + category
                        + "\nPrice: ₱" + Money.formatCents(priceCents)
                        + (savedPhotoPath == null ? "\nPhoto: TheMealDB exact-name lookup started" : "\nPhoto: " + savedPhotoPath));
                newMenuNameField.clear();
                newMenuPriceField.clear();
                newMenuPrepField.setText("15");
                selectedMenuPhoto[0] = null;
                newMenuPhotoPreview.setImage(null);
                selectedMenuPhotoLabel.setText("No photo selected — DineSmart will look for an exact TheMealDB dish photo.");
            } catch (IllegalArgumentException ex) {
                status.setText("Enter a valid selling price (up to two decimals) and preparation time.");
            } catch (SQLException | IOException | RuntimeException ex) {
                status.setText("Could not save this menu item: " + message(ex));
                output.setText(message(ex));
            }
        });

        VBox menuGalleryCard = contentCard(
                sectionTitle("Saved menu items", "Food photos and prices for dishes in your restaurant catalog"),
                menuPhotoGallery);
        VBox menuToolsCard = contentCard(
                sectionTitle("Menu catalog", "Search, select, and manage dishes available for service"),
                menuSearchRow, menuList,
                new Label("SELLING PRICE"),
                new HBox(10, priceField, updatePriceButton),
                new HBox(10, refreshMenuButton, deleteMenuButton, soldOutButton, dailyMenuButton));
        HBox.setHgrow(priceField, Priority.ALWAYS);
        VBox menuPage = page("Menu management", "Manage all items available in your restaurant",
                menuItemFormCard, menuGalleryCard, menuToolsCard);

        // Beverage management page
        beverageList = new ListView<>();
        beverageList.setPrefHeight(340);
        Button refreshBeverageListButton = new Button("Refresh beverage list");
        refreshBeverageListButton.getStyleClass().add("button-secondary");
        refreshBeverageListButton.setOnAction(e -> {
            try { beverageList.getItems().setAll(menuRepository.findByTypeSummaries("BEVERAGE")); }
            catch (SQLException | RuntimeException ex) { status.setText("Could not load beverages."); output.setText(message(ex)); }
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
                sectionTitle("Create customer order", "Build an order from dishes available on today's menu"),
                new HBox(10, customerNameField, orderTableField, orderItemChoice, orderQuantityField, addToCartButton),
                orderCartList, new HBox(10, removeCartItemButton, clearCartButton, cartTotalLabel),
                placeOrderButton);
        HBox.setHgrow(customerNameField, Priority.ALWAYS);
        HBox.setHgrow(orderTableField, Priority.NEVER);
        HBox.setHgrow(orderItemChoice, Priority.ALWAYS);
        VBox activeQueueCard = contentCard(
                sectionTitle("Kitchen order queue", "Pending and preparing orders appear here in arrival order"),
                orderQueueSummary, activeOrdersList,
                new HBox(10, refreshQueueButton, prepareOrderButton, completeOrderButton, cancelOrderButton),
                cancelLatestOrderButton);
        VBox orderHistoryCard = contentCard(
                sectionTitle("Recent order history", "Latest 100 orders, including completed and cancelled orders"), orderHistoryList);
        VBox readyToServeCard = contentCard(
                sectionTitle("Ready to serve", "Orders completed by the kitchen and waiting for waiter service"),
                readyToServeList,
                new HBox(10, refreshReadyToServeButton, markSelectedServedButton));
        VBox ordersPage = page("Customer orders", "Create orders, manage the kitchen queue, and track order status",
                createOrderCard, readyToServeCard, activeQueueCard, orderHistoryCard);
        ListView<String> cashierTotalsList = new ListView<>();
        cashierTotalsList.setPrefHeight(420);
        ListView<String> myOrdersTodayList = new ListView<>();
        myOrdersTodayList.setPrefHeight(150);
        Button refreshCashierTotals = new Button("Refresh totals");
        refreshCashierTotals.setOnAction(e -> {
            try {
                cashierTotalsList.getItems().setAll(orderReportRepository.findCashierTotals());
                myOrdersTodayList.getItems().setAll(activeStaffUserId[0] == null
                        ? List.<String>of()
                        : orderReportRepository.findOrdersCreatedByToday(activeStaffUserId[0]));
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not load cashier totals.");
                output.setText(message(ex));
            }
        });
        Button markSelectedPaidButton = new Button("Mark selected order as PAID");
        markSelectedPaidButton.setOnAction(e -> {
            String selected = cashierTotalsList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Select a customer bill first.");
                return;
            }
            String orderId = selected.split("\\s+\\|\\s+")[0].trim();
            if (selected.contains("PAYMENT: PAID")) {
                status.setText("Order " + orderId + " is already marked as paid.");
                return;
            }
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Confirm that payment has been received for order " + orderId + "?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Confirm payment received");
            confirmation.setHeaderText("Mark this customer bill as PAID?");
            confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
                try {
                    orderController.markPaid(activeRole[0], orderId);
                    refreshCashierTotals.fire();
                    status.setText("Order " + orderId + " marked as PAID.");
                } catch (SQLException | RuntimeException ex) {
                    status.setText("Could not mark this order as paid.");
                    output.setText(message(ex));
                    refreshCashierTotals.fire();
                }
            });
        });
        VBox cashierPage = page("Cashier", "Order totals for payment processing",
                contentCard(sectionTitle("Customer bills",
                                "Select an order after receiving payment, then mark it as PAID."),
                        refreshCashierTotals, cashierTotalsList, markSelectedPaidButton),
                contentCard(sectionTitle("My orders today",
                                "Only orders created by the currently signed-in staff account are shown."),
                        myOrdersTodayList));
        ListView<IngredientRepository.Ingredient> ingredientList = new ListView<>();
        ingredientList.setPrefHeight(390);
        ComboBox<IngredientRepository.Ingredient> stockIngredientChoice = new ComboBox<>();
        stockIngredientChoice.setPromptText("Select an existing ingredient");
        stockIngredientChoice.setMaxWidth(Double.MAX_VALUE);
        TextField stockAdjustmentAmount = new TextField("1");
        stockAdjustmentAmount.setPromptText("Amount to add or remove");
        Button addStockButton = new Button("+ Add quantity");
        Button removeStockButton = new Button("− Remove quantity");
        removeStockButton.getStyleClass().add("button-secondary");
        Button deleteIngredientButton = new Button("Delete selected ingredient");
        deleteIngredientButton.getStyleClass().add("button-secondary");
        Label selectedStockInfo = new Label("Select an ingredient to see its current stock and unit.");
        Button refreshIngredientButton = new Button("Refresh stock list"); refreshIngredientButton.getStyleClass().add("button-secondary");

        // New ingredients are created once; future stock changes use the selector and +/- controls below.
        TextField ingredientNameField = new TextField(); ingredientNameField.setPromptText("New ingredient name");
        TextField ingredientQuantityField = new TextField("0"); ingredientQuantityField.setPromptText("Opening stock (e.g. 500)");
        TextField ingredientUnitField = new TextField(); ingredientUnitField.setPromptText("Unit (g, kg, ml, pcs)");
        Button saveIngredientButton = new Button("Add new ingredient");

        Runnable refreshIngredients = () -> {
            try {
                List<IngredientRepository.Ingredient> rows = ingredientRepository.findAll();
                ingredientList.getItems().setAll(rows);
                String selectedName = stockIngredientChoice.getValue() == null ? null : stockIngredientChoice.getValue().name();
                stockIngredientChoice.getItems().setAll(rows);
                if (selectedName != null) {
                    rows.stream().filter(i -> i.name().equalsIgnoreCase(selectedName)).findFirst().ifPresent(stockIngredientChoice::setValue);
                }
                updateSelectedStockInfo(stockIngredientChoice.getValue(), selectedStockInfo);
            } catch (SQLException | RuntimeException ex) { status.setText("Could not load ingredient inventory."); output.setText(message(ex)); }
        };
        stockIngredientChoice.setOnAction(e -> updateSelectedStockInfo(stockIngredientChoice.getValue(), selectedStockInfo));
        ingredientList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
            if (selected != null) {
                stockIngredientChoice.getItems().stream()
                        .filter(i -> i.name().equalsIgnoreCase(selected.name()))
                        .findFirst().ifPresent(stockIngredientChoice::setValue);
            }
        });
        java.util.function.BiConsumer<Boolean, String> adjustSelectedStock = (add, actionName) -> {
            try {
                IngredientRepository.Ingredient selected = stockIngredientChoice.getValue();
                if (selected == null) { throw new IllegalArgumentException("Select an ingredient from the list first."); }
                double amount = Double.parseDouble(stockAdjustmentAmount.getText().trim());
                inventoryController.adjust(activeRole[0], selected.name(), amount, add);
                refreshIngredients.run();
                status.setText(actionName + " " + amount + " " + selected.unit() + " for " + selected.name() + ".");
            } catch (SQLException | RuntimeException ex) { status.setText("Could not update ingredient stock."); output.setText(message(ex)); new Alert(Alert.AlertType.WARNING, message(ex)).showAndWait(); }
        };
        addStockButton.setOnAction(e -> adjustSelectedStock.accept(true, "Added"));
        removeStockButton.setOnAction(e -> adjustSelectedStock.accept(false, "Removed"));
        deleteIngredientButton.setOnAction(e -> {
            IngredientRepository.Ingredient selected = stockIngredientChoice.getValue();
            if (selected == null) { status.setText("Select an ingredient to delete."); return; }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete " + selected.name() + "? Ingredients linked to recipes or stock history may be protected.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.setTitle("Delete ingredient");
            confirm.setHeaderText("Remove this ingredient from inventory?");
            confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
                try {
                    inventoryController.delete(activeRole[0], selected.name());
                    stockIngredientChoice.setValue(null);
                    selectedStockInfo.setText("Select an ingredient to see its current stock and unit.");
                    refreshIngredients.run();
                    status.setText("Ingredient deleted: " + selected.name());
                } catch (SQLException | RuntimeException ex) {
                    status.setText("Could not delete ingredient.");
                    output.setText(message(ex));
                    new Alert(Alert.AlertType.WARNING, message(ex)).showAndWait();
                }
            });
        });
        refreshIngredientButton.setOnAction(e -> refreshIngredients.run());
        saveIngredientButton.setOnAction(e -> {
            try {
                inventoryController.save(activeRole[0], ingredientNameField.getText(),
                        Double.parseDouble(ingredientQuantityField.getText().trim()),
                        ingredientUnitField.getText());
                ingredientNameField.clear(); ingredientQuantityField.setText("0"); ingredientUnitField.clear();
                refreshIngredients.run();
                status.setText("New ingredient added. Select it above to adjust stock later.");
            } catch (SQLException | RuntimeException ex) { status.setText("Could not add ingredient."); output.setText(message(ex)); new Alert(Alert.AlertType.WARNING, message(ex)).showAndWait(); }
        });
        HBox.setHgrow(stockIngredientChoice, Priority.ALWAYS);
        HBox stockAdjustmentRow = new HBox(10, stockIngredientChoice, stockAdjustmentAmount, addStockButton, removeStockButton, deleteIngredientButton);
        HBox.setHgrow(stockIngredientChoice, Priority.ALWAYS);
        HBox newIngredientForm = new HBox(10, ingredientNameField, ingredientQuantityField, ingredientUnitField, saveIngredientButton);
        HBox.setHgrow(ingredientNameField, Priority.ALWAYS);
        VBox stockAdjustmentCard = contentCard(
                sectionTitle("Update ingredient stock", "Record deliveries or adjust quantities after stock is used."),
                stockAdjustmentRow, selectedStockInfo);
        VBox addIngredientCard = contentCard(
                sectionTitle("Add ingredient", "Register a new ingredient with its starting quantity and unit."),
                newIngredientForm);
        VBox ingredientStockCard = contentCard(
                sectionTitle("Available ingredients", "Review current quantities and stock availability."),
                refreshIngredientButton, ingredientList);
        VBox inventoryPage = page("Ingredient inventory",
                "Check ingredient quantities and availability for daily preparation.",
                stockAdjustmentCard, addIngredientCard, ingredientStockCard);

        // Simplified inventory: physical stock is managed here; recipe requirements are edited inside each recipe.
        VBox inventoryEnhancedPage = inventoryPage;

        // Unified recipe library and menu scheduler. Recipes remain unpublished until scheduled.
        MenuPeriodRepository periodRepository = new MenuPeriodRepository();
        ListView<RecipeCatalogRepository.Recipe> libraryList = new ListView<>(); libraryList.setPrefHeight(190);
        ListView<MenuPeriodRepository.Period> periodList = new ListView<>(); periodList.setPrefHeight(190);
        Button refreshLibrary = new Button("Refresh Recipe Library"); refreshLibrary.getStyleClass().add("button-secondary");
        Button refreshPeriods = new Button("Refresh Schedule"); refreshPeriods.getStyleClass().add("button-secondary");
        Runnable loadLibrary = () -> { try { libraryList.getItems().setAll(recipeCatalogRepository.findAll()); } catch (SQLException | RuntimeException ex) { status.setText("Could not load recipe library."); output.setText(message(ex)); } };
        Runnable loadPeriods = () -> { try { periodList.getItems().setAll(periodRepository.findAll()); } catch (SQLException | RuntimeException ex) { status.setText("Could not load menu schedule."); output.setText(message(ex)); } };
        refreshLibrary.setOnAction(e -> loadLibrary.run()); refreshPeriods.setOnAction(e -> loadPeriods.run());

        TextField customName = new TextField(); customName.setPromptText("Unique restaurant recipe name");
        ChoiceBox<String> customCategory = new ChoiceBox<>(); customCategory.getItems().addAll("APPETIZER","MAIN_COURSE","BEVERAGE"); customCategory.setValue("MAIN_COURSE");
        ComboBox<IngredientRepository.Ingredient> customIngredientChoice = new ComboBox<>();
        customIngredientChoice.setPromptText("Choose an ingredient from inventory"); customIngredientChoice.setMaxWidth(Double.MAX_VALUE);
        TextField customIngredientQty = new TextField(); customIngredientQty.setPromptText("Measurement (e.g. 150 g, 1 handful, to taste)");
        Label customIngredientUnit = new Label("Any recipe measurement is accepted; only measurable compatible units are deducted from stock.");
        ListView<String> customIngredientList = new ListView<>(); customIngredientList.setPrefHeight(150);
        List<String> customIngredientLines = new ArrayList<>();
        Button addCustomIngredient = new Button("Add ingredient to recipe");
        Runnable refreshCustomIngredientChoices = () -> { try { customIngredientChoice.getItems().setAll(ingredientRepository.findAll()); } catch (SQLException | RuntimeException ex) { status.setText("Could not load inventory ingredients."); output.setText(message(ex)); } };
        customIngredientChoice.setOnAction(e -> { var selected=customIngredientChoice.getValue(); customIngredientUnit.setText(selected==null?"Any recipe measurement is accepted; only measurable compatible units are deducted from stock.":"Inventory unit: "+selected.unit()+" (used only when the entered measurement is compatible)"); });
        addCustomIngredient.setOnAction(e -> {
            try {
                var selected=customIngredientChoice.getValue();
                if(selected==null) { throw new IllegalArgumentException("Choose an ingredient from inventory first."); }
                String measurement=customIngredientQty.getText()==null?"":customIngredientQty.getText().trim();
                if(measurement.isBlank()) { throw new IllegalArgumentException("Enter a measurement, for example 150 g, 1 handful, or to taste."); }
                String line=selected.name()+" | "+measurement;
                if(customIngredientLines.stream().anyMatch(x->x.split("\\|",-1)[0].equalsIgnoreCase(selected.name()))) { throw new IllegalArgumentException("That ingredient is already in this recipe. Remove it from the list before adding it again."); }
                customIngredientLines.add(line); customIngredientList.getItems().add(selected.name()+"  ·  "+measurement);
                customIngredientQty.clear(); customIngredientChoice.getSelectionModel().clearSelection(); customIngredientUnit.setText("Choose an ingredient to see its stock unit");
            } catch (RuntimeException ex) { status.setText("Could not add recipe ingredient."); output.setText(message(ex)); }
        });
        Button removeCustomIngredient = new Button("Remove selected ingredient"); removeCustomIngredient.getStyleClass().add("button-secondary");
        removeCustomIngredient.setOnAction(e -> { int i=customIngredientList.getSelectionModel().getSelectedIndex(); if(i>=0){customIngredientLines.remove(i);customIngredientList.getItems().remove(i);} });
        TextArea customInstructions = new TextArea(); customInstructions.setPromptText("Preparation instructions"); customInstructions.setPrefRowCount(3); customInstructions.getStyleClass().add("recipe-text-area"); customInstructions.setWrapText(true);
        Button saveCustomRecipe = new Button("Save Restaurant Original Recipe");
        saveCustomRecipe.setOnAction(e -> {
            try {
                if (customName.getText() == null || customName.getText().isBlank()) {
                    throw new IllegalArgumentException("Enter a recipe name.");
                }
                if (customIngredientLines.isEmpty()) {
                    throw new IllegalArgumentException("Add at least one ingredient to the recipe.");
                }
                String savedName = customName.getText().trim();
                recipeCatalogRepository.saveCustom(savedName, customCategory.getValue(),
                        customInstructions.getText(), String.join("\n", customIngredientLines));
                loadLibrary.run();
                libraryList.getItems().stream()
                        .filter(r -> r.name().equalsIgnoreCase(savedName))
                        .findFirst()
                        .ifPresent(r -> libraryList.getSelectionModel().select(r));
                customName.clear();
                customIngredientLines.clear();
                customIngredientList.getItems().clear();
                customInstructions.clear();
                status.setText("SAVED: " + savedName + " is in Recipe Library and ready for scheduling.");
                output.setText("RESTAURANT RECIPE SAVED SUCCESSFULLY\n\n" + savedName
                        + " is now available in the Schedule a Menu tab.\n"
                        + "It is not published to customer ordering until scheduled.");
                new Alert(Alert.AlertType.INFORMATION,
                        "Recipe saved successfully!\n\n" + savedName
                        + " is now available for scheduling.\n"
                        + "Open Schedule a Menu to set its price and dates.").showAndWait();
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not save restaurant recipe.");
                output.setText(message(ex));
                new Alert(Alert.AlertType.ERROR, "Could not save recipe:\n" + message(ex)).showAndWait();
            }
        });
        refreshCustomIngredientChoices.run();

        // Review/edit ingredient quantities in the Recipe Library before publishing.
        ComboBox<RecipeCatalogRepository.IngredientRequirement> recipeIngredientEditorChoice = new ComboBox<>();
        recipeIngredientEditorChoice.setPromptText("Select an ingredient requirement"); recipeIngredientEditorChoice.setMaxWidth(Double.MAX_VALUE);
        TextField recipeEditorQuantity = new TextField(); recipeEditorQuantity.setPromptText("Quantity per serving");
        ChoiceBox<String> recipeEditorUnit = new ChoiceBox<>();
        recipeEditorUnit.getItems().addAll("g","kg","mg","oz","lb","ml","l","fl oz","tsp","tbsp","cup","qt","gal","pcs","pinch","can","bunch","slice","clove","bottle","packet","stick","head","stalk","sprig");
        recipeEditorUnit.setValue("g");
        ListView<RecipeCatalogRepository.IngredientRequirement> recipeEditorList = new ListView<>(); recipeEditorList.setPrefHeight(155);
        Label recipeEditorWarning = new Label("Select a saved recipe to review its ingredient measurements.");
        recipeEditorWarning.setWrapText(true);
        Runnable refreshRecipeIngredientEditor = () -> {
            RecipeCatalogRepository.Recipe selectedRecipe = libraryList.getSelectionModel().getSelectedItem();
            try {
                if (selectedRecipe == null) { recipeEditorList.getItems().clear(); recipeIngredientEditorChoice.getItems().clear(); recipeEditorWarning.setText("Select a saved recipe to review its ingredient measurements."); return; }
                var requirements = recipeCatalogRepository.findIngredients(selectedRecipe.id());
                recipeEditorList.getItems().setAll(requirements);
                recipeIngredientEditorChoice.getItems().setAll(requirements);
                if (requirements.isEmpty()) {
                    recipeEditorWarning.setText("This saved recipe has no ingredient records yet. Re-import its full TheMealDB details or add ingredients when creating a restaurant original.");
                    return;
                }
                var inventory = ingredientRepository.findAll();
                long needsReview = requirements.stream().filter(r -> !"READY".equals(r.status())).count();
                long unmatched = requirements.stream().filter(r -> inventory.stream().noneMatch(i -> i.name().equalsIgnoreCase(r.name()))).count();
                recipeEditorWarning.setText("All recipe measurements are accepted, including descriptive amounts such as handful, chopped, juice of 3 limes, and to taste. Add missing ingredient names to inventory with zero stock. Automatic deductions apply only when a numeric quantity and compatible stock unit are available.");
            } catch (SQLException | RuntimeException ex) { status.setText("Could not load recipe ingredient details."); output.setText(message(ex)); }
        };
        // Save TheMealDB recipes, then refresh and select the saved entry so it is visibly ready to schedule.
        importButton.setOnAction(event -> {
            MealRecipe selected = results.getSelectionModel().getSelectedItem();
            if (selected == null) { return; }
            importButton.setDisable(true);
            status.setText("Saving recipe to the library...");
            CompletableFuture.supplyAsync(() -> {
                try {
                    MealRecipe complete = selected.ingredients().isEmpty() ? api.lookupById(selected.id()) : selected;
                    recipeCatalogRepository.saveMealDb(complete);
                    return complete;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Recipe import was interrupted.", ex);
                } catch (java.sql.SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }, ioExecutor).whenComplete((saved, error) -> Platform.runLater(() -> {
                importButton.setDisable(false);
                if (error != null) {
                    status.setText("Could not save recipe.");
                    output.setText(message(error));
                    new Alert(Alert.AlertType.ERROR, "Could not save recipe:\n" + message(error)).showAndWait();
                } else {
                    loadLibrary.run();
                    libraryList.getItems().stream()
                            .filter(r -> r.name().equalsIgnoreCase(saved.name()))
                            .findFirst().ifPresent(r -> libraryList.getSelectionModel().select(r));
                    refreshRecipeIngredientEditor.run();
                    status.setText("SAVED: " + saved.name() + " is in Recipe Library and ready for scheduling.");
                    output.setText("RECIPE SAVED SUCCESSFULLY\n\n" + saved.name()
                            + " is now available in the Schedule a Menu tab.\n"
                            + "It is not published to customer ordering until you schedule it.\n\n"
                            + formatRecipe(saved));
                    new Alert(Alert.AlertType.INFORMATION,
                            "Recipe saved successfully!\n\n" + saved.name()
                            + " is now available for scheduling.\n"
                            + "Open Seasonal & Meal Menus → Schedule a Menu to set its price and dates.").showAndWait();
                }
            }));
        });

        libraryList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> refreshRecipeIngredientEditor.run());
        recipeIngredientEditorChoice.setOnAction(e -> {
            var requirement = recipeIngredientEditorChoice.getValue();
            if (requirement == null) { return; }
            if (requirement.quantity() != null) { recipeEditorQuantity.setText(String.valueOf(requirement.quantity())); } else { recipeEditorQuantity.clear(); }
            if (requirement.unit() != null && recipeEditorUnit.getItems().contains(requirement.unit())) { recipeEditorUnit.setValue(requirement.unit()); }
        });
        Button saveRecipeIngredientEdit = new Button("Save ingredient measurement");
        saveRecipeIngredientEdit.setOnAction(e -> {
            var recipe = libraryList.getSelectionModel().getSelectedItem();
            var requirement = recipeIngredientEditorChoice.getValue();
            try {
                if (recipe == null || requirement == null) { throw new IllegalArgumentException("Select a recipe and one of its ingredients first."); }
                recipeCatalogRepository.updateIngredient(recipe.id(), requirement.name(), Double.parseDouble(recipeEditorQuantity.getText().trim()), recipeEditorUnit.getValue());
                refreshRecipeIngredientEditor.run();
                status.setText("Saved " + requirement.name() + " measurement. Review unit compatibility before scheduling.");
            } catch (SQLException | RuntimeException ex) { status.setText("Could not update recipe ingredient."); output.setText(message(ex)); }
        });
        Button refreshRecipeIngredientEditorButton = new Button("Refresh ingredient review"); refreshRecipeIngredientEditorButton.getStyleClass().add("button-secondary");
        refreshRecipeIngredientEditorButton.setOnAction(e -> refreshRecipeIngredientEditor.run());
        Button addMissingRecipeIngredients = new Button("Add missing ingredients to inventory (zero stock)");
        addMissingRecipeIngredients.getStyleClass().add("button-secondary");
        addMissingRecipeIngredients.setOnAction(e -> {
            RecipeCatalogRepository.Recipe selected = libraryList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                new Alert(Alert.AlertType.INFORMATION, "Select a recipe in the Recipe Library first.").showAndWait();
                return;
            }
            try {
                RecipeCatalogRepository.MissingIngredientResult result =
                        recipeCatalogRepository.addMissingInventoryIngredientsDetailed(selected.id());
                refreshRecipeIngredientEditor.run();
                refreshIngredients.run();
                refreshCustomIngredientChoices.run();

                StringBuilder details = new StringBuilder();
                details.append("Added to inventory with zero stock: ").append(result.added()).append("\n");
                details.append("Already in inventory: ").append(result.alreadyPresent()).append("\n");
                details.append("Need measurement review: 0 (descriptive measurements are accepted)\n");
                if (!result.reviewNames().isEmpty()) {
                    details.append("\nPlease correct these measurements first:\n");
                    result.reviewNames().forEach(name -> details.append("• ").append(name).append("\n"));
                }
                if (!result.unitConflicts().isEmpty()) {
                    details.append("\nUnit differences to check:\n");
                    result.unitConflicts().forEach(name -> details.append("• ").append(name).append("\n"));
                }
                status.setText(result.added() + " ingredient(s) added; " + result.alreadyPresent() + " already existed. Recipe measurements were accepted.");
                new Alert(Alert.AlertType.INFORMATION, details.toString()).showAndWait();
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not add missing ingredients.");
                output.setText(message(ex));
                new Alert(Alert.AlertType.ERROR, "Could not add missing ingredients:\\n" + message(ex)).showAndWait();
            }
        });

        TextField schedulePrice = new TextField(); schedulePrice.setPromptText("Selling price (PHP)");
        TextField schedulePrep = new TextField("15"); schedulePrep.setPromptText("Prep minutes");
        ChoiceBox<String> periodType = new ChoiceBox<>(); periodType.getItems().addAll("BREAKFAST","LUNCH","DINNER","SEASONAL","SPECIAL"); periodType.setValue("DINNER");
        DatePicker periodStart = new DatePicker(java.time.LocalDate.now());
        DatePicker periodEnd = new DatePicker(java.time.LocalDate.now().plusDays(7));
        Button savePeriod = new Button("Save Schedule & Publish on Selected Dates");
        savePeriod.setOnAction(e -> {
            RecipeCatalogRepository.Recipe chosen = libraryList.getSelectionModel().getSelectedItem();
            if (chosen == null) { status.setText("Select a recipe from the library first."); return; }
            try {
                long priceCents = Money.parseCents(schedulePrice.getText());
                int prep = Integer.parseInt(schedulePrep.getText().trim());
                if (periodStart.getValue() == null || periodEnd.getValue() == null) { throw new IllegalArgumentException("Choose both schedule dates."); }
                periodRepository.scheduleRecipe(chosen.id(), priceCents, prep, periodType.getValue(),
                        periodStart.getValue().toString(), periodEnd.getValue().toString());
                loadPeriods.run(); refreshMenu(menuList); refreshMenuChart(menuChart);
                status.setText("Recipe scheduled. It will appear in ordering only during its active date range.");
                output.setText(chosen.name() + " scheduled as " + periodType.getValue() + " from " + periodStart.getValue() + " to " + periodEnd.getValue() + ".\nSelling price: ₱" + Money.formatCents(priceCents));
            } catch (SQLException | RuntimeException ex) { status.setText("Could not schedule recipe."); output.setText(message(ex)); }
        });
        TabPane menuSchedulerTabs = new TabPane();
        menuSchedulerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab libraryTab = new Tab("1. Recipe Library", new VBox(12,
                contentCard(sectionTitle("Browse saved recipes", "Choose a TheMealDB recipe or an admin-created restaurant original. Saving a recipe does not publish it."), refreshLibrary, libraryList),
                contentCard(sectionTitle("Review recipe ingredients", "1. Select an ingredient. 2. Correct its quantity and choose a real stock unit. 3. Save the measurement. 4. Add the missing ingredients to inventory."), refreshRecipeIngredientEditorButton, recipeEditorList, recipeIngredientEditorChoice, new HBox(8,recipeEditorQuantity,recipeEditorUnit), saveRecipeIngredientEdit, addMissingRecipeIngredients, recipeEditorWarning)));
        Tab createRecipeTab = new Tab("2. Save New Recipe", new VBox(12,
                contentCard(sectionTitle("Create a restaurant original", "Choose an ingredient and enter any measurement or preparation description, such as 150 g, 1 handful, chopped, or to taste."), customName, customCategory,
                        new HBox(8,customIngredientChoice,customIngredientQty), customIngredientUnit, addCustomIngredient, customIngredientList, removeCustomIngredient, customInstructions, saveCustomRecipe)));
        Tab scheduleTab = new Tab("3. Schedule a Menu", new VBox(12,
                contentCard(sectionTitle("Publish a recipe", "First select a recipe in Recipe Library or save a new one. Then set its price, preparation time, meal period, and dates."), new HBox(8,schedulePrice,schedulePrep), new HBox(8,periodType,periodStart,periodEnd), savePeriod)));
        Button toggleScheduleStatus = new Button("Activate / Deactivate Selected Schedule");
        toggleScheduleStatus.getStyleClass().add("button-secondary");
        toggleScheduleStatus.setOnAction(e -> {
            MenuPeriodRepository.Period selectedPeriod = periodList.getSelectionModel().getSelectedItem();
            if (selectedPeriod == null) { status.setText("Select a scheduled menu first."); return; }
            try {
                periodRepository.setActive(selectedPeriod.id(), !selectedPeriod.active());
                loadPeriods.run();
                refreshMenu(menuList);
                refreshOrderChoices.run();
                status.setText(selectedPeriod.active() ? "Schedule deactivated. The dish is no longer published by this schedule." : "Schedule activated.");
            } catch (SQLException | RuntimeException ex) { status.setText("Could not update schedule."); output.setText(message(ex)); }
        });
        Tab scheduledTab = new Tab("4. Scheduled Menus", new VBox(12,
                contentCard(sectionTitle("Manage scheduled menus", "Review upcoming, active, and expired schedules. Deactivating a schedule removes the dish from ordering without deleting its recipe or order history."), refreshPeriods, periodList, toggleScheduleStatus)));
        menuSchedulerTabs.getTabs().addAll(libraryTab,createRecipeTab,scheduleTab,scheduledTab);
        menuSchedulerTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == createRecipeTab) { refreshCustomIngredientChoices.run(); }
            if (newTab == libraryTab) { refreshRecipeIngredientEditor.run(); }
        });
        VBox menuPeriodsPage = page("Seasonal & meal menus", "A simple four-step workflow: browse recipes, save restaurant originals, schedule dishes, and manage availability",
                contentCard(sectionTitle("How it works", "1. Choose or create a recipe.  2. Set its selling price and schedule dates.  3. Review scheduled menus. Only active schedules publish dishes during their date range."), menuSchedulerTabs));
        loadLibrary.run(); loadPeriods.run();

        // Reservation book for guest seating and table planning.
        ListView<ReservationRepository.Reservation> reservationList = new ListView<>(); reservationList.setPrefHeight(330);
        TextField guestName = new TextField(); guestName.setPromptText("Guest name");
        TextField guestPhone = new TextField(); guestPhone.setPromptText("Contact number");
        TextField partySize = new TextField("2"); partySize.setPromptText("Guests");
        TextField reservationDateTime = new TextField(); reservationDateTime.setPromptText("YYYY-MM-DD HH:MM");
        TextField tableLabel = new TextField(); tableLabel.setPromptText("Table / section");
        TextField reservationNotes = new TextField(); reservationNotes.setPromptText("Occasion, dietary notes, VIP requests");
        Button createReservation = new Button("Create reservation");
        Button refreshReservations = new Button("Refresh reservations"); refreshReservations.getStyleClass().add("button-secondary");
        Button seatReservation = new Button("Mark seated");
        Button cancelReservation = new Button("Cancel reservation"); cancelReservation.getStyleClass().add("button-danger");
        Runnable loadReservations = () -> { try { reservationList.getItems().setAll(reservationController.list(activeRole[0])); } catch (SQLException | RuntimeException ex) { status.setText("Could not load reservations."); output.setText(message(ex)); } };
        resetRestaurantButton.setOnAction(e -> {
            Alert confirmReset = new Alert(Alert.AlertType.CONFIRMATION,
                    "This permanently clears menu items, recipes, ingredients, stock, orders, reservations, and reports. Staff login accounts will be kept. This cannot be undone. Continue?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirmReset.setTitle("Reset restaurant data");
            confirmReset.setHeaderText("Start the restaurant setup again?");
            confirmReset.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {
                try {
                    Database.resetRestaurantData();
                    refreshMenu(menuList); refreshMenuChart(menuChart);
                    refreshIngredients.run(); refreshOrders.run(); loadReservations.run(); loadPeriods.run();
                    refreshDashboardMetrics();
                    output.setText("Restaurant data has been cleared. Staff accounts were preserved. You can now add your own ingredients, recipes, menus, and reservations.");
                    status.setText("Fresh restaurant setup is ready.");
                } catch (SQLException | RuntimeException ex) {
                    status.setText("Could not reset restaurant data.");
                    output.setText(message(ex));
                }
            });
        });
        loadReservations.run();
        createReservation.setOnAction(e -> { try { reservationController.book(activeRole[0], guestName.getText(), guestPhone.getText(),
                    Integer.parseInt(partySize.getText()), reservationDateTime.getText(),
                    tableLabel.getText(), reservationNotes.getText()); loadReservations.run(); status.setText("Guest reservation created."); } catch (SQLException | RuntimeException ex) { status.setText("Could not create reservation."); output.setText(message(ex)); } });
        refreshReservations.setOnAction(e -> loadReservations.run());
        seatReservation.setOnAction(e -> { var r=reservationList.getSelectionModel().getSelectedItem(); if(r==null)return; try { reservationController.updateStatus(activeRole[0], r.id(), "SEATED"); loadReservations.run(); } catch (SQLException | RuntimeException ex) { output.setText(message(ex)); } });
        cancelReservation.setOnAction(e -> { var r=reservationList.getSelectionModel().getSelectedItem(); if(r==null)return; try { reservationController.updateStatus(activeRole[0], r.id(), "CANCELLED"); loadReservations.run(); } catch (SQLException | RuntimeException ex) { output.setText(message(ex)); } });
        VBox reservationPage = page("Reservations & tables", "Manage guest bookings, party sizes, seating, and special requests",
                contentCard(sectionTitle("New reservation", "Use local restaurant date and time, for example 2026-11-12 19:30."),
                        new HBox(8,guestName,guestPhone),new HBox(8,partySize,reservationDateTime,tableLabel),reservationNotes,createReservation),
                contentCard(sectionTitle("Upcoming reservations", "Select a booking to seat or cancel it."),refreshReservations,reservationList,new HBox(8,seatReservation,cancelReservation)));

        // Reports page
        CategoryAxis reportCategoryAxis = new CategoryAxis();
        reportCategoryAxis.setLabel("Menu category");
        NumberAxis reportCountAxis = new NumberAxis();
        reportCountAxis.setLabel("Saved items");
        reportChart = new BarChart<>(reportCategoryAxis, reportCountAxis);
        reportChart.setTitle("Menu category frequency");
        reportChart.setLegendVisible(false);
        reportChart.setPrefHeight(430);
        Button reportRefreshButton = new Button("Refresh report");
        reportRefreshButton.getStyleClass().add("button-secondary");
        reportRefreshButton.setOnAction(e -> {
            try { refreshMenuChart(reportChart); status.setText("Reports refreshed from SQLite."); }
            catch (SQLException | RuntimeException ex) { status.setText("Could not refresh reports."); output.setText(message(ex)); }
        });
        Button refreshSalesButton = new Button("Refresh sales charts");
        refreshSalesButton.getStyleClass().add("button-secondary");
        refreshSalesButton.setOnAction(e -> {
            try { refreshBestSellerChart(salesChart); refreshSalesSummary(todaySalesLabel, todayOrdersLabel); status.setText("Sales analytics refreshed from completed orders."); }
            catch (SQLException | RuntimeException ex) { status.setText("Could not refresh sales analytics."); output.setText(message(ex)); }
        });
        VBox salesSummaryCard = contentCard(sectionTitle("Today's sales",
                        "Only completed orders are counted as sales"),
                new HBox(24, new VBox(5, new Label("NET ORDER SALES"), todaySalesLabel),
                        new VBox(5, new Label("COMPLETED ORDERS"), todayOrdersLabel)),
                refreshSalesButton);
        ListView<String> staffSalesList = new ListView<>();
        staffSalesList.setPrefHeight(170);
        Button refreshStaffSalesButton = new Button("Refresh staff sales");
        refreshStaffSalesButton.getStyleClass().add("button-secondary");
        refreshStaffSalesButton.setOnAction(e -> {
            try {
                staffSalesList.getItems().setAll(reportController
                        .todaySalesByStaffCents(activeRole[0]).entrySet().stream()
                        .map(entry -> entry.getKey() + " — ₱"
                                + Money.formatGroupedCents(entry.getValue()))
                        .toList());
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not load daily sales by staff.");
                output.setText(message(ex));
            }
        });
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
            return orderReportRepository.analyzeHistoricalOrderLog();
        } catch (SQLException | RuntimeException ex) {
            throw new RuntimeException(ex);
        }
    }, ioExecutor).whenComplete((result, error) ->
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

                } catch (SQLException | RuntimeException ex) {
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
            catch (SQLException | RuntimeException ex) { status.setText("Could not refresh hourly analysis."); output.setText(message(ex)); }
        });
        TextField recommendationItemField = new TextField();
        recommendationItemField.setPromptText("Suggested dish or menu item");
        TextArea recommendationReasonField = new TextArea();
        recommendationReasonField.setPromptText("Why should this dish be added? (customer demand, sales trends, ingredient availability, etc.)");
        recommendationReasonField.setPrefRowCount(2);
        recommendationReasonField.setWrapText(true);
        ListView<MenuRecommendationRepository.Recommendation> recommendationList = new ListView<>();
        recommendationList.setPrefHeight(160);
        Button submitRecommendationButton = new Button("Submit menu recommendation");
        Button refreshRecommendationsButton = new Button("Refresh recommendations");
        refreshRecommendationsButton.getStyleClass().add("button-secondary");
        Runnable refreshRecommendations = () -> {
            try { recommendationList.getItems().setAll(recommendationRepository.findAll()); }
            catch (SQLException | RuntimeException ex) { status.setText("Could not load menu recommendations."); output.setText(message(ex)); }
        };
        submitRecommendationButton.setOnAction(e -> {
            try {
                recommendationRepository.submit(recommendationItemField.getText(),
                        recommendationReasonField.getText(), activeDisplayName[0]);
                recommendationItemField.clear();
                recommendationReasonField.clear();
                refreshRecommendations.run();
                status.setText("Menu recommendation submitted for Manager/Admin review.");
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not submit menu recommendation.");
                output.setText(message(ex));
                new Alert(Alert.AlertType.WARNING, message(ex)).showAndWait();
            }
        });
        refreshRecommendationsButton.setOnAction(e -> refreshRecommendations.run());

        VBox reportsPage = page("Reports & insights", "Understand menu performance, sales, and peak ordering periods",
                salesSummaryCard,
                contentCard(sectionTitle("Daily sales by staff",
                                "Completed order sales are grouped by the staff account that created each order."),
                        refreshStaffSalesButton, staffSalesList),
                contentCard(sectionTitle("Best-selling foods & beverages", "Top items ranked by quantities in completed orders"), salesChart),
                contentCard(sectionTitle("Orders by hour", "Hourly order volume and busiest restaurant period"), peakHourLabel, hourlyOrdersChart, refreshHourlyChartButton, analyzeHistoryButton, historyAnalysisLabel),
                contentCard(sectionTitle("Menu category analysis", "Live SQL aggregation of saved menu items"),
                        new Label("This chart is generated from the current SQLite menu data."), reportRefreshButton, reportChart),
                contentCard(sectionTitle("Menu recommendations", "Supervisors can suggest dishes for Manager/Admin review. Suggestions do not change the live menu."),
                        recommendationItemField, recommendationReasonField,
                        new HBox(8, submitRecommendationButton, refreshRecommendationsButton), recommendationList));


        // Staff account administration (supervisor/admin only).
        TextField staffUsernameField = new TextField();
        staffUsernameField.setPromptText("Username");
        TextField staffDisplayNameField = new TextField();
        staffDisplayNameField.setPromptText("Display name");
        PasswordField staffPasswordField = new PasswordField();
        staffPasswordField.setPromptText("Temporary password (8+ characters)");
        PasswordField staffResetPasswordField = new PasswordField();
        staffResetPasswordField.setPromptText("New password (8+ characters)");
        ChoiceBox<String> staffRoleChoice = new ChoiceBox<>();
        staffRoleChoice.getItems().addAll("SUPERVISOR", "WAITER", "CASHIER", "KITCHEN");
        staffRoleChoice.setValue("WAITER");
        ListView<AuthRepository.User> staffUsersList = new ListView<>();
        staffUsersList.setPrefHeight(280);
        Button refreshStaffUsersButton = new Button("Refresh staff");
        Button createStaffUserButton = new Button("Create staff account");
        Button disableStaffUserButton = new Button("Disable selected");
        Button enableStaffUserButton = new Button("Enable selected");
        Button resetStaffPasswordButton = new Button("Reset selected password");
        refreshStaffUsersButton.getStyleClass().add("button-secondary");
        disableStaffUserButton.getStyleClass().add("button-danger");
        Runnable refreshStaffUsers = () -> {
            try {
                staffUsersList.getItems().setAll(authController.staffUsers(activeRole[0]));
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not load staff accounts.");
                output.setText(message(ex));
            }
        };
        createStaffUserButton.setOnAction(event -> {
            try {
                authController.createStaffUser(activeRole[0], staffUsernameField.getText(),
                        staffPasswordField.getText(), staffDisplayNameField.getText(),
                        staffRoleChoice.getValue());
                staffUsernameField.clear();
                staffDisplayNameField.clear();
                staffPasswordField.clear();
                refreshStaffUsers.run();
                status.setText("Staff account created with a salted password hash.");
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not create staff account.");
                output.setText(message(ex));
            }
        });
        disableStaffUserButton.setOnAction(event -> {
            AuthRepository.User selected = staffUsersList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Select a staff account first.");
                return;
            }
            try {
                authController.setStaffUserActive(activeRole[0],
                        selected.staffUserId(), false);
                refreshStaffUsers.run();
                status.setText(selected.username() + " has been disabled.");
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not disable staff account.");
                output.setText(message(ex));
            }
        });
        enableStaffUserButton.setOnAction(event -> {
            AuthRepository.User selected = staffUsersList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Select a staff account first.");
                return;
            }
            try {
                authController.setStaffUserActive(activeRole[0],
                        selected.staffUserId(), true);
                refreshStaffUsers.run();
                status.setText(selected.username() + " has been enabled.");
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not enable staff account.");
                output.setText(message(ex));
            }
        });
        resetStaffPasswordButton.setOnAction(event -> {
            AuthRepository.User selected = staffUsersList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Select a staff account first.");
                return;
            }
            try {
                authController.resetStaffPassword(activeRole[0],
                        selected.staffUserId(), staffResetPasswordField.getText());
                staffResetPasswordField.clear();
                status.setText("Password reset for " + selected.username() + ".");
            } catch (SQLException | RuntimeException ex) {
                status.setText("Could not reset staff password.");
                output.setText(message(ex));
            }
        });
        refreshStaffUsersButton.setOnAction(event -> refreshStaffUsers.run());
        VBox staffPage = page("Staff management",
                "Create, disable, enable, and reset staff accounts",
                contentCard(sectionTitle("Create staff account",
                                "Passwords must contain at least 8 characters and are stored as salted PBKDF2 hashes."),
                        new HBox(8, staffUsernameField, staffDisplayNameField),
                        new HBox(8, staffPasswordField, staffRoleChoice),
                        createStaffUserButton),
                contentCard(sectionTitle("Existing staff accounts",
                                "Select an account to enable, disable, or reset its password."),
                        refreshStaffUsersButton, staffUsersList,
                        new HBox(8, enableStaffUserButton, disableStaffUserButton),
                        staffResetPasswordField, resetStaffPasswordButton));

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
        Button dashboardNav = navButton("⌂", "Home");
        Button menuNav = navButton("▤", "Menu management");
        Button ordersNav = navButton("▣", "Customer orders");
        Button cashierNav = navButton("₱", "Cashier");
        Button beverageNav = navButton("◉", "Beverages");
        Button recipeNav = navButton("⌕", "Recipe discovery");
        Button reportsNav = navButton("▥", "Reports & insights");
        Button inventoryNav = navButton("◈", "Ingredient inventory");
        Button seasonalNav = navButton("✦", "Seasonal & meal menus");
        Button reservationNav = navButton("▦", "Reservations & tables");
        Button staffNav = navButton("♙", "Staff management");
        VBox navigation = new VBox(7, navLabel, dashboardNav, menuNav, ordersNav, cashierNav,
                beverageNav, inventoryNav, seasonalNav, reservationNav, recipeNav, reportsNav,
                staffNav);
        navigation.getStyleClass().add("navigation");

        Label sidebarFooter = new Label("DineSmart  |  CPE 314\nRestaurant Ordering & Inventory Insights");
        sidebarFooter.getStyleClass().add("sidebar-footer");
        Region sidebarSpacer = new Region();
        VBox.setVgrow(sidebarSpacer, Priority.ALWAYS);
        VBox sidebar = new VBox(25, brandBlock, navigation, sidebarSpacer, sidebarFooter);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(228);
        sidebar.setMinWidth(210);

        Label topTitle = new Label("Dashboard");
        topTitle.getStyleClass().add("topbar-title");
        Label topHint = new Label("Your restaurant at a glance");
        topHint.getStyleClass().add("topbar-hint");
        VBox topHeading = new VBox(3, topTitle, topHint);
        Label statusDot = new Label("●  SYSTEM READY");
        statusDot.getStyleClass().add("system-status");
        Label accountChip = new Label("STAFF");
        accountChip.getStyleClass().add("account-chip");
        accountChip.setVisible(false);
        accountChip.setManaged(false);
        Button logoutButton = new Button("LOG OUT  ↗");
        logoutButton.getStyleClass().add("button-logout");
        logoutButton.setVisible(false);
        logoutButton.setManaged(false);
        HBox topbar = new HBox(topHeading, statusDot, accountChip, logoutButton);
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
        pages.put("Cashier", cashierPage);
        pages.put("Beverages", beveragePage);
        pages.put("Recipe discovery", recipePage);
        pages.put("Reports & insights", reportsPage);
        pages.put("Ingredient inventory", inventoryEnhancedPage);
        pages.put("Seasonal & meal menus", menuPeriodsPage);
        pages.put("Reservations & tables", reservationPage);
        pages.put("Staff management", staffPage);
        java.util.Map<String, Button> navButtons = new java.util.LinkedHashMap<>();
        navButtons.put("Dashboard", dashboardNav); navButtons.put("Menu management", menuNav);
        navButtons.put("Customer orders", ordersNav); navButtons.put("Cashier", cashierNav); navButtons.put("Beverages", beverageNav);
        navButtons.put("Recipe discovery", recipeNav); navButtons.put("Reports & insights", reportsNav);
        navButtons.put("Ingredient inventory", inventoryNav);
        navButtons.put("Seasonal & meal menus", seasonalNav);
        navButtons.put("Reservations & tables", reservationNav);
        navButtons.put("Staff management", staffNav);

        java.util.function.Consumer<String> showPage = pageName -> {
            pageHost.getChildren().setAll(pages.get(pageName));
            topTitle.setText(pageName);
            topHint.setText(switch (pageName) {
                case "Dashboard" -> "Your restaurant at a glance";
                case "Menu management" -> "Maintain your restaurant catalog and pricing";
                case "Customer orders" -> "Create customer orders and manage the kitchen queue";
                case "Cashier" -> "Review order totals and payment amounts";
                case "Beverages" -> "Manage drinks and beverage pricing";
                case "Recipe discovery" -> "Discover food recipes with TheMealDB";
                default -> "Review menu data and category performance";
            });
            navButtons.forEach((name, button) -> {
                button.getStyleClass().remove("nav-button-active");
                if (name.equals(pageName)) { button.getStyleClass().add("nav-button-active"); }
            });
        };
        dashboardNav.setOnAction(e -> showPage.accept("Dashboard"));
        menuFeatureCard.setOnAction(e -> menuNav.fire());
        ordersFeatureCard.setOnAction(e -> ordersNav.fire());
        inventoryFeatureCard.setOnAction(e -> inventoryNav.fire());
        reportsFeatureCard.setOnAction(e -> reportsNav.fire());
        menuNav.setOnAction(e -> {
            try {
                if ("WAITER".equals(activeRole[0])) { menuList.getItems().setAll(menuRepository.findAvailableSummaries()); }
                else menuList.getItems().setAll(menuRepository.findAllPriceSummaries());
            }
            catch (SQLException | RuntimeException ex) { status.setText("Could not load today's available menu."); output.setText(message(ex)); }
            showPage.accept("Menu management");
        });
        ordersNav.setOnAction(e -> { refreshOrderChoices.run(); refreshOrders.run(); showPage.accept("Customer orders"); });
        cashierNav.setOnAction(e -> { refreshCashierTotals.fire(); showPage.accept("Cashier"); });
        beverageNav.setOnAction(e -> {
            try { beverageList.getItems().setAll(menuRepository.findByTypeSummaries("BEVERAGE")); }
            catch (SQLException | RuntimeException ex) { status.setText("Could not load beverages."); output.setText(message(ex)); }
            showPage.accept("Beverages");
        });
        recipeNav.setOnAction(e -> showPage.accept("Recipe discovery"));
        inventoryNav.setOnAction(e -> { refreshIngredients.run(); showPage.accept("Ingredient inventory"); });
        seasonalNav.setOnAction(e -> { loadPeriods.run(); showPage.accept("Seasonal & meal menus"); });
        reservationNav.setOnAction(e -> { loadReservations.run(); showPage.accept("Reservations & tables"); });
        staffNav.setOnAction(e -> { refreshStaffUsers.run(); showPage.accept("Staff management"); });
        reportsNav.setOnAction(e -> {
            try { refreshMenuChart(reportChart); refreshRecommendations.run(); }
            catch (SQLException | RuntimeException ex) { status.setText("Could not refresh reports."); output.setText(message(ex)); }
            showPage.accept("Reports & insights");
        });
        showPage.accept("Dashboard");

        // Compact application status strip
        HBox statusStrip = new HBox(status);
        statusStrip.getStyleClass().add("status-strip");
        shell.setBottom(statusStrip);

        stage.setTitle("DineSmart | Restaurant Ordering & Inventory Insights");
        Scene scene = new Scene(shell, 1440, 920);
        var stylesheet = getClass().getResource("/styles/dinesmart.css");
        if (stylesheet != null) { scene.getStylesheets().add(stylesheet.toExternalForm()); }
        stage.setMinWidth(1120); stage.setMinHeight(760);

        Database.initialize();
        authRepository.seedDemoAccounts();
        VBox loginPane = new VBox(16);
        loginPane.getStyleClass().add("login-screen");
        Label loginBrand = new Label("DINESMART"); loginBrand.getStyleClass().add("login-brand");
        Label loginSub = new Label("RESTAURANT ORDERING & INVENTORY INSIGHTS"); loginSub.getStyleClass().add("login-subtitle");
        Label loginTitle = new Label("Sign in to your account"); loginTitle.getStyleClass().add("login-title");
        Label loginHint = new Label("Welcome back. Please enter your staff credentials."); loginHint.getStyleClass().add("login-hint");
        TextField usernameField = new TextField(); usernameField.setPromptText("Username"); usernameField.setMaxWidth(330);
        PasswordField passwordField = new PasswordField(); passwordField.setPromptText("Password"); passwordField.setMaxWidth(330);
        Label loginError = new Label(""); loginError.getStyleClass().add("login-error");
        Button loginButton = new Button("SIGN IN"); loginButton.setMaxWidth(330);
        Label demoHint = new Label("Demo accounts: admin / admin123 · supervisor / super123 · waiter / waiter123 · cashier / cashier123 · kitchen / kitchen123");
        demoHint.getStyleClass().add("login-demo"); demoHint.setWrapText(true); demoHint.setMaxWidth(440);
        VBox loginCard = new VBox(13, loginBrand, loginSub, loginTitle, loginHint,
                usernameField, passwordField, loginButton, loginError, demoHint);
        loginCard.getStyleClass().add("login-card");
        loginCard.setAlignment(Pos.CENTER);
        loginCard.setMaxWidth(430);
        loginCard.setPrefWidth(430);

        loginPane.getChildren().add(loginCard);
        loginPane.setAlignment(Pos.CENTER);
        loginPane.setFillWidth(false);

        // Open directly to staff login; the separate Get Started welcome page is removed.
        scene.setRoot(loginPane);
        Runnable performLogin = () -> {
            try {
                AuthRepository.User user = authController.login(usernameField.getText(), passwordField.getText());
                if (user == null) { loginError.setText("Invalid username or password, or account is inactive."); return; }
                String role = user.role();
                activeRole[0] = role;
                activeDisplayName[0] = user.displayName();
                activeStaffUserId[0] = user.staffUserId();
                dashboardNav.setVisible(StaffPermissions.canViewDashboard(role)); dashboardNav.setManaged(dashboardNav.isVisible());
                menuNav.setVisible(StaffPermissions.canViewMenuManagement(role)); menuNav.setManaged(menuNav.isVisible());
                ordersNav.setVisible(StaffPermissions.canViewCustomerOrders(role)); ordersNav.setManaged(ordersNav.isVisible());
                cashierNav.setVisible(StaffPermissions.canViewCashier(role)); cashierNav.setManaged(cashierNav.isVisible());
                beverageNav.setVisible(StaffPermissions.canViewBeverages(role)); beverageNav.setManaged(beverageNav.isVisible());
                recipeNav.setVisible(StaffPermissions.canViewRecipeDiscovery(role)); recipeNav.setManaged(recipeNav.isVisible());
                reportsNav.setVisible(StaffPermissions.canViewReports(role)); reportsNav.setManaged(reportsNav.isVisible());
                submitRecommendationButton.setVisible(StaffPermissions.canSubmitRecommendations(role)); submitRecommendationButton.setManaged(submitRecommendationButton.isVisible());
                recommendationItemField.setVisible(StaffPermissions.canSubmitRecommendations(role)); recommendationItemField.setManaged(recommendationItemField.isVisible());
                recommendationReasonField.setVisible(StaffPermissions.canSubmitRecommendations(role)); recommendationReasonField.setManaged(recommendationReasonField.isVisible());
                refreshRecommendationsButton.setVisible(StaffPermissions.canReviewRecommendations(role)); refreshRecommendationsButton.setManaged(refreshRecommendationsButton.isVisible());
                recommendationList.setVisible(StaffPermissions.canReviewRecommendations(role)); recommendationList.setManaged(recommendationList.isVisible());
                inventoryNav.setVisible(StaffPermissions.canViewInventory(role)); inventoryNav.setManaged(inventoryNav.isVisible());
                // Kitchen staff can review ingredient availability, while stock maintenance remains supervisor/admin-only.
                boolean canManageIngredientStock = StaffPermissions.canManageIngredientStock(role);
                stockAdjustmentCard.setVisible(canManageIngredientStock); stockAdjustmentCard.setManaged(canManageIngredientStock);
                addIngredientCard.setVisible(canManageIngredientStock); addIngredientCard.setManaged(canManageIngredientStock);
                ingredientStockCard.setVisible(true); ingredientStockCard.setManaged(true);
                seasonalNav.setVisible(StaffPermissions.canViewSeasonalMenus(role)); seasonalNav.setManaged(seasonalNav.isVisible());
                reservationNav.setVisible(StaffPermissions.canViewReservations(role)); reservationNav.setManaged(reservationNav.isVisible());
                staffNav.setVisible(StaffPermissions.canManageStaff(role));
                staffNav.setManaged(staffNav.isVisible());
                saveIngredientButton.setVisible(StaffPermissions.canManageIngredientStock(role)); saveIngredientButton.setManaged(saveIngredientButton.isVisible());
                addStockButton.setVisible(StaffPermissions.canManageIngredientStock(role)); addStockButton.setManaged(addStockButton.isVisible());
                removeStockButton.setVisible(StaffPermissions.canManageIngredientStock(role)); removeStockButton.setManaged(removeStockButton.isVisible());
                stockAdjustmentAmount.setVisible(StaffPermissions.canManageIngredientStock(role)); stockAdjustmentAmount.setManaged(stockAdjustmentAmount.isVisible());
                stockIngredientChoice.setVisible(StaffPermissions.canManageIngredientStock(role)); stockIngredientChoice.setManaged(stockIngredientChoice.isVisible());
                selectedStockInfo.setVisible(StaffPermissions.canManageIngredientStock(role)); selectedStockInfo.setManaged(selectedStockInfo.isVisible());
                resetRestaurantButton.setVisible(StaffPermissions.canResetRestaurantData(role)); resetRestaurantButton.setManaged(resetRestaurantButton.isVisible());
                newIngredientForm.setVisible(StaffPermissions.canManageIngredientStock(role)); newIngredientForm.setManaged(newIngredientForm.isVisible());
                deleteIngredientButton.setVisible(StaffPermissions.canManageIngredientStock(role)); deleteIngredientButton.setManaged(deleteIngredientButton.isVisible());
                createOrderCard.setVisible(StaffPermissions.canCreateOrders(role)); createOrderCard.setManaged(createOrderCard.isVisible());
                activeQueueCard.setVisible(StaffPermissions.canViewActiveOrderQueue(role)); activeQueueCard.setManaged(activeQueueCard.isVisible());
                orderHistoryCard.setVisible(StaffPermissions.canViewOrderHistory(role)); orderHistoryCard.setManaged(orderHistoryCard.isVisible());
                boolean canMarkOrdersServed = StaffPermissions.canMarkOrdersServed(role);
                readyToServeCard.setVisible(canMarkOrdersServed); readyToServeCard.setManaged(canMarkOrdersServed);
                markSelectedServedButton.setVisible(canMarkOrdersServed); markSelectedServedButton.setManaged(canMarkOrdersServed);
                refreshReadyToServeButton.setVisible(canMarkOrdersServed); refreshReadyToServeButton.setManaged(canMarkOrdersServed);
                boolean canMarkOrdersPaid = StaffPermissions.canMarkOrdersPaid(role);
                markSelectedPaidButton.setVisible(canMarkOrdersPaid); markSelectedPaidButton.setManaged(canMarkOrdersPaid);
                prepareOrderButton.setVisible(StaffPermissions.canPrepareOrders(role));
                prepareOrderButton.setManaged(prepareOrderButton.isVisible());
                completeOrderButton.setVisible(StaffPermissions.canCompleteOrders(role));
                completeOrderButton.setManaged(completeOrderButton.isVisible());
                cancelOrderButton.setVisible(StaffPermissions.canCancelOrders(role)); cancelOrderButton.setManaged(cancelOrderButton.isVisible());
                cancelLatestOrderButton.setVisible(StaffPermissions.canCancelOrders(role)); cancelLatestOrderButton.setManaged(cancelLatestOrderButton.isVisible());
                updatePriceButton.setVisible(StaffPermissions.canUpdateMenuPrices(role)); updatePriceButton.setManaged(updatePriceButton.isVisible());
                deleteMenuButton.setVisible(StaffPermissions.canDeleteMenuItems(role)); deleteMenuButton.setManaged(deleteMenuButton.isVisible());
                soldOutButton.setVisible(StaffPermissions.canToggleSoldOut(role)); soldOutButton.setManaged(soldOutButton.isVisible());
                dailyMenuButton.setVisible(StaffPermissions.canManageDailyMenu(role)); dailyMenuButton.setManaged(dailyMenuButton.isVisible());
                boolean canCreateMenuItems = StaffPermissions.canCreateMenuItems(role);
                menuItemFormCard.setVisible(canCreateMenuItems); menuItemFormCard.setManaged(canCreateMenuItems);
                refreshMenuButton.setVisible(StaffPermissions.canRefreshMenu(role)); refreshMenuButton.setManaged(refreshMenuButton.isVisible());
                topHint.setText(user.displayName() + "  ·  " + role);
                accountChip.setText(user.displayName() + "  ·  " + role);
                accountChip.setVisible(true);
                accountChip.setManaged(true);
                logoutButton.setVisible(true);
                logoutButton.setManaged(true);
                status.setText("Signed in as " + user.displayName() + " (" + role + ").");
                if (role.equals("CASHIER")) { cashierNav.fire(); }
                else if (role.equals("WAITER")) { refreshOrderChoices.run(); refreshOrders.run(); showPage.accept("Customer orders"); }
                else if (role.equals("KITCHEN")) { refreshOrders.run(); refreshIngredients.run(); showPage.accept("Customer orders"); }
                else {
                    if (role.equals("SUPERVISOR")) { refreshIngredients.run(); refreshRecommendations.run(); }
                    showPage.accept("Dashboard");
                }
                scene.setRoot(shell);
            } catch (SQLException | RuntimeException ex) { loginError.setText("Sign-in failed: " + message(ex)); }
        };

        // Log out safely and return to the centered staff login screen.
        logoutButton.setOnAction(e -> {
            Alert confirmation = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to log out of DineSmart?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Confirm logout");
            confirmation.setHeaderText("End this staff session?");
            confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(choice -> {
                activeRole[0] = "";
                authController.logout();
                activeStaffUserId[0] = null;
                activeDisplayName[0] = "";
                usernameField.clear();
                passwordField.clear();
                loginError.setText("");
                logoutButton.setVisible(false);
                logoutButton.setManaged(false);
                accountChip.setVisible(false);
                accountChip.setManaged(false);
                scene.setRoot(loginPane);
                status.setText("You have logged out. Sign in to continue.");
            });
        });

        loginButton.setOnAction(e -> performLogin.run());
        passwordField.setOnAction(e -> performLogin.run());
        stage.setScene(scene); stage.show();

        // Load existing menu entries and refresh dashboard figures at startup.
        try {
            menuRepository.deleteInvalidRows();
            refreshMenu(menuList);
            refreshMenuChart(menuChart);
            refreshMenuChart(reportChart);
            beverageList.getItems().setAll(menuRepository.findByTypeSummaries("BEVERAGE"));
            updateMetric(mainCourseMetric, "MAIN_COURSE");
            updateMetric(appetizerMetric, "APPETIZER");
            updateMetric(beverageMetric, "BEVERAGE");
            refreshOrderChoices.run(); refreshOrders.run(); refreshIngredients.run();
            refreshBestSellerChart(salesChart); refreshSalesSummary(todaySalesLabel, todayOrdersLabel);
            refreshHourlyOrdersChart(hourlyOrdersChart, peakHourLabel);
        } catch (SQLException | RuntimeException ex) {
            status.setText("Menu not loaded yet. Use Initialize Database if needed.");
            output.setText(message(ex));
        }
        // Alert once per order and alert stage so the same warning is not shown every poll.
        java.util.Set<String> alreadyAlerted = new java.util.HashSet<>();
        java.util.Set<String> overdueAlerted = new java.util.HashSet<>();
        java.util.Set<String> urgentAlerted = new java.util.HashSet<>();
        Timeline delayMonitor = new Timeline(new KeyFrame(Duration.seconds(30), event -> {
            try {
                boolean staffView = scene.getRoot() == shell
                        && java.util.Set.of("ADMIN", "SUPERVISOR", "WAITER", "KITCHEN").contains(activeRole[0]);
                if (!staffView) { return; }
                refreshOrders.run();

                // Default early warning: five minutes before the estimated preparation deadline.
                for (String item : orderRepository.findOrdersApproachingPreparationDeadline(5)) {
                    String orderId = item.split(" — ", 2)[0];
                    if (alreadyAlerted.add(orderId)) {
                        status.setText("PREPARATION ALMOST DUE: Order " + orderId);
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Kitchen preparation reminder");
                        alert.setHeaderText("Order is approaching its estimated completion time");
                        alert.setContentText(item + "\nPlease check kitchen progress and prepare to serve.");
                        alert.show();
                    }
                }

                // At the estimated deadline, warn that the order is now overdue.
                for (String item : orderRepository.findDelayedOrders(0)) {
                    String orderId = item.split(" — ", 2)[0];
                    if (overdueAlerted.add(orderId)) {
                        status.setText("PREPARATION OVERDUE: Order " + orderId);
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Kitchen order overdue");
                        alert.setHeaderText("Estimated preparation time has been reached");
                        alert.setContentText(item + "\nPlease update the order or notify the supervisor.");
                        alert.show();
                    }
                }

                // Escalate once after ten minutes beyond the estimate.
                for (String item : orderRepository.findDelayedOrders(10)) {
                    String orderId = item.split(" — ", 2)[0];
                    if (urgentAlerted.add(orderId)) {
                        status.setText("URGENT KITCHEN DELAY: Order " + orderId);
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Urgent kitchen delay");
                        alert.setHeaderText("Order is more than 10 minutes overdue");
                        alert.setContentText(item + "\nSupervisor attention is required.");
                        alert.show();
                    }
                }
            } catch (SQLException | RuntimeException ex) {
                status.setText("Kitchen alert monitor could not refresh: " + ex.getMessage());
            }
        }));
        delayMonitor.setCycleCount(Timeline.INDEFINITE);
        delayMonitor.play();
    }

    @Override
    public void stop() {
        ioExecutor.shutdownNow();
    }

    private VBox page(String heading, String subtitle, javafx.scene.Node... content) {
        // Photo-led workspace header. The app's welcome/login screens use the
        // supplied flat-lay photo, while every workspace uses its own food image.
        boolean dashboard = "Dashboard".equals(heading);
        Label eyebrow = new Label(dashboard
                ? "WELCOME TO YOUR RESTAURANT"
                : "DINESMART  /  RESTAURANT OPERATIONS");
        eyebrow.getStyleClass().add("hero-eyebrow");
        Label title = new Label(dashboard ? "Welcome to DineSmart" : heading);
        title.getStyleClass().add("hero-title");
        Label description = new Label(dashboard
                ? "Restaurant Ordering & Inventory Insights"
                : subtitle);
        description.getStyleClass().add("hero-subtitle");
        description.setWrapText(true);
        VBox copy = new VBox(8, eyebrow, title, description);
        copy.getStyleClass().add("hero-copy");
        copy.setMaxWidth(650);

        String imageName = switch (heading) {
            case "Dashboard", "Recipe discovery" -> "hero-dessert.jpg";
            case "Menu management", "Seasonal & meal menus" -> "hero-menu.jpg";
            case "Beverage management" -> "hero-beverages.jpg";
            case "Customer orders" -> "hero-kitchen.jpg";
            case "Cashier", "Reservations & tables" -> "hero-dining.jpg";
            case "Ingredient inventory" -> "hero-kitchen.jpg";
            case "Reports & insights" -> "hero-dining.jpg";
            default -> "hero-menu.jpg";
        };

        StackPane hero = new StackPane();
        hero.getStyleClass().add("page-hero");
        hero.setMinHeight(dashboard ? 218 : 198);
        hero.setPrefHeight(dashboard ? 218 : 198);
        hero.setMaxWidth(Double.MAX_VALUE);

        ImageView heroImage = null;
        var imageUrl = getClass().getResource("/images/" + imageName);
        double imageWidth = dashboard ? 360 : 310;
        double imageHeight = dashboard ? 184 : 164;
        if (imageUrl != null) {
            // Load the native-resolution source first; let ImageView scale only at render time.
            // This avoids baking a small, soft thumbnail into the dashboard hero.
            Image image = new Image(imageUrl.toExternalForm(), 0, 0, true, true);
            heroImage = new ImageView(image);
            heroImage.setFitWidth(imageWidth);
            heroImage.setFitHeight(imageHeight);
            heroImage.setPreserveRatio(false);
            heroImage.setSmooth(true);
            Rectangle imageClip = new Rectangle(imageWidth, imageHeight);
            imageClip.setArcWidth(22);
            imageClip.setArcHeight(22);
            heroImage.setClip(imageClip);
        }
        StackPane imageFrame = new StackPane();
        imageFrame.getStyleClass().add("hero-image-frame");
        imageFrame.setMinSize(imageWidth, imageHeight);
        imageFrame.setPrefSize(imageWidth, imageHeight);
        imageFrame.setMaxSize(imageWidth, imageHeight);
        if (heroImage != null) { imageFrame.getChildren().add(heroImage); }

        HBox heroContent = new HBox(dashboard ? 34 : 26, copy, imageFrame);
        heroContent.setAlignment(Pos.CENTER_LEFT);
        heroContent.setPadding(new Insets(12, 24, 12, 30));
        HBox.setHgrow(copy, Priority.ALWAYS);
        hero.getChildren().add(heroContent);

        VBox body = new VBox(18);
        body.getChildren().addAll(content);
        body.setPadding(new Insets(8, 4, 30, 4));
        body.getStyleClass().add("page-content");
        VBox wrapper = new VBox(18, hero, body);
        wrapper.setPadding(new Insets(22, 28, 24, 28));
        wrapper.getStyleClass().add("page-wrapper");
        // Give the ingredient workspace a real culinary photo backdrop instead
        // of leaving the full page as a flat cream surface. Other workspaces keep
        // their existing visual treatment.
        if ("Ingredient inventory".equals(heading)) {
            wrapper.getStyleClass().add("inventory-page-wrapper");
        } else if ("Dashboard".equals(heading)) {
            wrapper.getStyleClass().add("dashboard-page-wrapper");
        }
        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        return new VBox(scroll);
    }

    private Button photoFeatureCard(String imageName, String heading, String description) {
        double width = 218;
        double imageHeight = 112;
        ImageView imageView = new ImageView();
        imageView.setFitWidth(width);
        imageView.setFitHeight(imageHeight);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        var imageUrl = getClass().getResource("/images/" + imageName);
        if (imageUrl != null) {
            // Keep the original pixels and scale at display time for a cleaner result.
            imageView.setImage(new Image(imageUrl.toExternalForm(), 0, 0, true, true));
        }
        Rectangle clip = new Rectangle(width, imageHeight);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        imageView.setClip(clip);

        Label title = new Label(heading);
        title.getStyleClass().add("feature-card-title");
        Label detail = new Label(description);
        detail.getStyleClass().add("feature-card-description");
        detail.setWrapText(true);
        VBox copy = new VBox(5, title, detail);
        copy.setAlignment(Pos.CENTER_LEFT);
        copy.setPadding(new Insets(10, 13, 13, 13));
        VBox content = new VBox(0, imageView, copy);
        content.setPrefWidth(width);
        content.setMaxWidth(width);

        Button card = new Button();
        card.setGraphic(content);
        card.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(Insets.EMPTY);
        card.setPrefSize(width + 2, 174);
        card.setMinSize(width + 2, 174);
        card.setMaxSize(width + 2, 174);
        card.getStyleClass().add("feature-card");
        card.setAccessibleText(heading + ". " + description);
        return card;
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

    private void updateMetric(Label metric, String itemType) throws SQLException {
        int count = menuRepository.findByTypeSummaries(itemType).size();
        metric.setText(String.valueOf(count));
    }

    private Label sectionTitle(String heading, String subtitle) {
        Label label = new Label(heading + "\n" + subtitle);
        label.getStyleClass().add("section-title");
        return label;
    }

    /** Copies a selected photo into DineSmart's persistent per-user image folder. */
    private static String saveMenuItemPhoto(File source, String itemName) throws IOException {
        if (source == null) { throw new IOException("Choose a valid image file."); }
        return MenuPhotoStorage.saveUploadedPhoto(
                source.toPath(), itemName, MenuPhotoStorage.defaultImageFolder()).toString();
    }

    /** Rebuilds the menu photo gallery from saved SQLite rows. Missing photos are matched by exact dish name on TheMealDB. */
    private void refreshMenuPhotoGallery(FlowPane gallery) throws SQLException {
        if (gallery == null) { return; }
        gallery.getChildren().clear();
        List<MenuItemRepository.MenuCard> items = menuRepository.findMenuCards();
        if (items.isEmpty()) {
            Label empty = new Label("No menu items have been saved yet. Add a dish to start your photo menu.");
            empty.getStyleClass().add("menu-gallery-empty");
            empty.setWrapText(true);
            gallery.getChildren().add(empty);
            return;
        }
        for (MenuItemRepository.MenuCard item : items) {
            gallery.getChildren().add(createMenuPhotoCard(item, gallery));
        }
    }

    private VBox createMenuPhotoCard(MenuItemRepository.MenuCard item, FlowPane gallery) {
        double cardWidth = 188;
        double photoWidth = cardWidth - 18;
        double photoHeight = 116;
        Image image = loadSavedMenuPhoto(item.imagePath(), photoWidth, photoHeight);
        javafx.scene.Node photoNode;
        if (image != null && !image.isError()) {
            ImageView photo = new ImageView(image);
            photo.setFitWidth(photoWidth);
            photo.setFitHeight(photoHeight);
            photo.setPreserveRatio(false);
            photo.setSmooth(true);
            Rectangle clip = new Rectangle(photoWidth, photoHeight);
            clip.setArcWidth(12); clip.setArcHeight(12);
            photo.setClip(clip);
            photoNode = photo;
        } else {
            String message = mealPhotoLookupMessages.getOrDefault(item.id(), "Looking for an exact dish photo on TheMealDB…");
            Label placeholderText = new Label(message);
            placeholderText.setWrapText(true);
            placeholderText.setAlignment(Pos.CENTER);
            placeholderText.getStyleClass().add("menu-photo-placeholder-text");
            StackPane placeholder = new StackPane(placeholderText);
            placeholder.setPrefSize(photoWidth, photoHeight);
            placeholder.setMinSize(photoWidth, photoHeight);
            placeholder.setMaxSize(photoWidth, photoHeight);
            placeholder.getStyleClass().add("menu-photo-placeholder");
            photoNode = placeholder;
            scheduleMealDbPhotoLookup(item, gallery);
        }

        Label name = new Label(item.name());
        name.getStyleClass().add("menu-item-photo-name");
        name.setWrapText(true);
        Label category = new Label(item.itemType().replace('_', ' '));
        category.getStyleClass().add("menu-item-photo-category");
        Label price = new Label("₱" + Money.formatCents(item.priceCents()));
        price.getStyleClass().add("menu-item-photo-price");
        VBox text = new VBox(4, name, category, price);
        text.setPadding(new Insets(7, 9, 10, 9));
        VBox card = new VBox(7, photoNode, text);
        card.setPadding(new Insets(8));
        card.setPrefWidth(cardWidth);
        card.setMinWidth(cardWidth);
        card.setMaxWidth(cardWidth);
        card.getStyleClass().add("menu-item-photo-card");
        return card;
    }

    private Image loadSavedMenuPhoto(String storedPath, double width, double height) {
        if (storedPath == null || storedPath.isBlank()) { return null; }
        try {
            Path path = Path.of(storedPath);
            if (!Files.isRegularFile(path)) { return null; }
            Image image = new Image(path.toUri().toString(), width, height, false, true, true);
            return image.isError() ? null : image;
        } catch (InvalidPathException | SecurityException ignored) {
            return null;
        }
    }

    /**
     * Looks up a menu item's exact name, downloads the verified MealDB thumbnail,
     * stores it in the user's local photo folder, and writes its path + MealDB ID
     * to SQLite. No generic dessert/main-course photo is substituted.
     */
    private void scheduleMealDbPhotoLookup(MenuItemRepository.MenuCard item, FlowPane gallery) {
        if (item == null || mealPhotoLookupsAttempted.contains(item.id())
                || !mealPhotoLookupsInProgress.add(item.id())) {
            return;
        }
        mealPhotoLookupMessages.put(item.id(), "Looking for an exact dish photo on TheMealDB…");
        CompletableFuture.runAsync(() -> {
            String resultMessage;
            try {
                MealRecipe meal = null;
                if (item.apiMealId() != null && !item.apiMealId().isBlank()) {
                    try { meal = api.lookupById(item.apiMealId()); }
                    catch (IOException ignored) { meal = null; }
                }
                if (meal == null) { meal = api.findExactByName(item.name()); }
                if (meal == null || meal.thumbnailUrl() == null || meal.thumbnailUrl().isBlank()) {
                    resultMessage = "No exact TheMealDB photo match. Choose a photo to upload.";
                } else {
                    String localPhoto = saveTheMealDbPhoto(meal, item.name());
                    menuRepository.updateMealDbPhoto(item.id(), localPhoto, meal.id(), item.imagePath());
                    resultMessage = "TheMealDB photo saved.";
                }
            } catch (IOException | InterruptedException | SQLException | RuntimeException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                resultMessage = "Photo lookup failed. Select Refresh Menu to try again.";
            } finally {
                mealPhotoLookupsInProgress.remove(item.id());
            }
            mealPhotoLookupsAttempted.add(item.id());
            final String displayMessage = resultMessage;
            Platform.runLater(() -> {
                mealPhotoLookupMessages.put(item.id(), displayMessage);
                try { refreshMenuPhotoGallery(gallery); }
                catch (SQLException refreshError) {
                    status.setText("Could not refresh the menu photo gallery.");
                    output.setText(message(refreshError));
                }
            });
        }, ioExecutor);
    }

    /** Downloads and persists an exact-match TheMealDB thumbnail locally. */
    private String saveTheMealDbPhoto(MealRecipe meal, String itemName) throws IOException, InterruptedException {
        byte[] photoBytes = api.downloadThumbnail(meal.thumbnailUrl());
        String urlPath = java.net.URI.create(meal.thumbnailUrl()).getPath().toLowerCase(java.util.Locale.ROOT);
        String extension = urlPath.endsWith(".png") ? ".png" : urlPath.endsWith(".webp") ? ".webp" : ".jpg";
        return MenuPhotoStorage.saveDownloadedPhoto(photoBytes, itemName, meal.id(), extension,
                MenuPhotoStorage.defaultImageFolder()).toString();
    }

    private void refreshMenu(ListView<String> menuList)
            throws SQLException {
        menuList.getItems().setAll(menuRepository.findAllPriceSummaries());
        refreshMenuPhotoGallery(menuPhotoGallery);
        refreshDashboardMetrics();
    }

    private void refreshDashboardMetrics() {
        try {
            if (mainCourseMetric != null) { updateMetric(mainCourseMetric, "MAIN_COURSE"); }
            if (appetizerMetric != null) { updateMetric(appetizerMetric, "APPETIZER"); }
            if (beverageMetric != null) { updateMetric(beverageMetric, "BEVERAGE"); }
        } catch (SQLException refreshError) {
            status.setText("Dashboard metrics could not be refreshed.");
            output.setText(message(refreshError));
        }
    }


    private void refreshMenuChart(BarChart<String, Number> chart)
            throws SQLException {
        java.util.Map<String, Integer> minedCounts =
                StaffPermissions.canViewReports(activeRole[0])
                        ? reportController.menuCategoryFrequency(activeRole[0])
                        : menuRepository.countByItemType();

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

    private void refreshBestSellerChart(BarChart<String, Number> chart) throws SQLException {
        chart.getData().clear();
        XYChart.Series<String, Number> food = new XYChart.Series<>();
        food.setName("Food");
        XYChart.Series<String, Number> beverages = new XYChart.Series<>();
        beverages.setName("Beverages");
        List<OrderReportRepository.BestSeller> bestSellers =
                StaffPermissions.canViewReports(activeRole[0])
                        ? reportController.bestSellers(activeRole[0], 8)
                        : orderReportRepository.findBestSellers(8);
        for (OrderReportRepository.BestSeller item : bestSellers) {
            boolean drink = "BEVERAGE".equalsIgnoreCase(item.type());
            (drink ? beverages : food).getData().add(new XYChart.Data<>(item.name(), item.quantity()));
        }
        if (!food.getData().isEmpty()) { chart.getData().add(food); }
        if (!beverages.getData().isEmpty()) { chart.getData().add(beverages); }
    }

    private void refreshHourlyOrdersChart(LineChart<String, Number> chart, Label peakLabel) throws SQLException {
        java.util.Map<Integer, Integer> hourly = orderReportRepository.getOrdersByHour();
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

    private void refreshSalesSummary(Label sales, Label orders) throws SQLException {
        long salesCents = StaffPermissions.canViewReports(activeRole[0])
                ? reportController.todaySalesCents(activeRole[0])
                : orderReportRepository.getTodaySalesCents();
        sales.setText("₱" + Money.formatGroupedCents(salesCents));
        orders.setText(orderRepository.getTodayOrderCount() + " completed orders");
    }

    private static void updateSelectedStockInfo(IngredientRepository.Ingredient ingredient, Label label) {
        if (ingredient == null) {
            label.setText("Select an ingredient to see its current stock and unit.");
        } else {
            label.setText(String.format("Selected: %s  |  Current stock: %,.2f %s",
                    ingredient.name(), ingredient.quantity(), ingredient.unit()));
        }
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
