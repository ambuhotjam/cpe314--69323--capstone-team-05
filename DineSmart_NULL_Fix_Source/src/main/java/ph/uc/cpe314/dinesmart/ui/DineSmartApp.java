
package ph.uc.cpe314.dinesmart.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import ph.uc.cpe314.dinesmart.data.Database;
import ph.uc.cpe314.dinesmart.data.MealRecipeRepository;
import ph.uc.cpe314.dinesmart.data.MenuItemRepository;
import ph.uc.cpe314.dinesmart.model.*;
import ph.uc.cpe314.dinesmart.service.KitchenQueue;
import ph.uc.cpe314.dinesmart.service.TheMealDbService;

import java.util.concurrent.CompletableFuture;

public class DineSmartApp extends Application {

    private final TheMealDbService api = new TheMealDbService();

    private final MealRecipeRepository recipeRepository =
            new MealRecipeRepository();

    private final MenuItemRepository menuRepository =
            new MenuItemRepository();

    @Override
    public void start(Stage stage) {

        Label title = new Label(
                "DineSmart — Restaurant Ordering & Inventory Insights");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label status = new Label(
                "Search recipes, import them, and manage restaurant prices.");

        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.setPrefRowCount(5);

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

        TextField priceField = new TextField();
        priceField.setPromptText("Enter selling price, e.g. 199.00");

        Button refreshMenuButton = new Button("Refresh Menu");
        Button updatePriceButton = new Button("Set / Update Price");
        Button deleteMenuButton = new Button("Delete Selected Menu");

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

        Button searchButton = new Button("Search TheMealDB");

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

            CompletableFuture.runAsync(() -> {
                try {
                    recipeRepository.importRecipe(selected);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).whenComplete((ignored, error) ->
                    Platform.runLater(() -> {
                        importButton.setDisable(false);

                        if (error != null) {
                            status.setText("Recipe import failed.");
                            output.setText(message(error));
                        } else {
                            status.setText(
                                    "Recipe imported. Set its selling "
                                    + "price in Menu Management.");

                            output.setText(
                                    "Imported from TheMealDB:\n"
                                    + selected.name()
                                    + " (API meal ID "
                                    + selected.id() + ")\n\n"
                                    + "Ingredients saved: "
                                    + selected.ingredients().size()
                                    + "\n\n"
                                    + formatRecipe(selected));

                            try {
                                refreshMenu(menuList);
                            } catch (Exception ex) {
                                output.appendText(
                                        "\nCould not refresh menu: "
                                        + message(ex));
                            }
                        }
                    }));
        });

        // Layout for menu search and menu buttons
        HBox menuSearchRow = new HBox(
                8,
                menuSearchField,
                searchSavedMenuButton,
                showAllMenusButton
        );

        HBox.setHgrow(menuSearchField, Priority.ALWAYS);

        HBox menuButtons = new HBox(
                8,
                refreshMenuButton,
                updatePriceButton,
                deleteMenuButton
        );

        HBox searchRow = new HBox(8, searchField, searchButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // Main application layout
        VBox root = new VBox(
                9,
                title,
                status,
                dbButton,
                new Separator(),

                new Label(
                        "Menu Management — Search, update, or delete saved menus"),
                menuSearchRow,
                menuList,
                priceField,
                menuButtons,

                new Separator(),

                new Label("TheMealDB Recipe Import"),
                searchRow,
                results,
                new Label("Selected recipe details"),
                recipeDetails,
                importButton,

                new Label("Database / Import output"),
                output
        );

        root.setPadding(new Insets(16));

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);

        stage.setTitle("DineSmart — Week 09 Milestone 1");
        stage.setScene(new Scene(scrollPane, 900, 850));
        stage.show();

        // Load existing menu entries when the app opens
        try {
            Database.initialize();
            menuRepository.deleteInvalidRows();
            refreshMenu(menuList);
        } catch (Exception ex) {
            status.setText(
                    "Menu not loaded yet. Use Initialize Database if needed.");
            output.setText(message(ex));
        }
    }

    private void refreshMenu(ListView<String> menuList)
            throws Exception {

        menuList.getItems().setAll(
                menuRepository.findAllPriceSummaries());
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
