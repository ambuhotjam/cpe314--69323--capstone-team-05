
## 5. API Integration Design

DineSmart uses **TheMealDB API**, a free public REST API that provides food recipes, meal categories, ingredients, cooking instructions, and meal images. It is integrated into the Recipe Discovery feature, allowing restaurant administrators to search for recipes, view their details, and import selected recipes into the system.

**API Base URL:**  
https://www.themealdb.com/api/json/v1/1/

**Official API Documentation:**  
https://www.themealdb.com/api.php

The application uses the development test key `1`.

### 5.1 API Endpoints Used

1. **Search Meal by Name – `search.php?s={meal_name}`**  
   Searches for recipes based on the meal name entered by the administrator and retrieves detailed meal information.

2. **Filter Meals by Category – `filter.php?c={category}`**  
   Retrieves meals belonging to a selected category, such as Main Course or Starter. The results include the meal ID, name, and thumbnail image.

3. **Lookup Meal by ID – `lookup.php?i={meal_id}`**  
   Retrieves the complete recipe information of a selected meal using its unique ID, including ingredients, measurements, cooking instructions, category, and cuisine origin.

### 5.2 JSON Response Structure

TheMealDB returns data in JSON format under a `meals` array. Each meal object contains fields such as:

- `idMeal` — Meal ID
- `strMeal` — Meal name
- `strCategory` — Meal category
- `strArea` — Cuisine origin
- `strInstructions` — Cooking instructions
- `strMealThumb` — Meal image URL
- `strIngredient1` to `strIngredient20` — Ingredients
- `strMeasure1` to `strMeasure20` — Ingredient measurements

If no matching meal is found, the API may return a null value for the `meals` array.

### 5.3 JSON Parsing and Data Usage

DineSmart uses Java's `HttpClient` to send GET requests to TheMealDB API and receive JSON responses. The `org.json` library, specifically `JSONObject` and `JSONArray`, is used to parse the response and extract the required meal information.

The extracted data is converted into `MealRecipe` objects, including the recipe details and up to 20 ingredient and measurement pairs. Empty ingredient entries are ignored.

The retrieved information is displayed in the Recipe Discovery interface, where administrators can search, browse, and review recipes.

When a recipe is selected for import, DineSmart retrieves its complete details when necessary and saves the recipe and its ingredients into the local SQLite database through `MealRecipeRepository`.

### 5.4 Meaningful Integration in the Application

TheMealDB API makes DineSmart more efficient by allowing administrators to discover recipes and import ingredients without manually entering all recipe information.

The integration connects external recipe data with the application's local SQLite database and menu management feature, making recipe discovery and menu preparation more convenient.

## 6. Concurrency Design

### 6.1 Long-Running Operations
DineSmart performs several operations that may take additional time to complete, particularly those involving external API requests and database processing. These operations include:
* **API Recipe Search:** Sends a request to **TheMealDB API** and retrieves matching recipes based on the user's search input.
* **Category Browsing:** Retrieves available recipes from a selected category (e.g., Main Course, Starter).
* **Recipe Import:** Retrieves complete recipe details when necessary and saves the recipe along with its associated ingredients into the local **SQLite database**.
* **Historical Order Analysis:** Processes saved restaurant order records to generate a summary of total orders, completed vs. cancelled orders, peak ordering hours, and top-selling items.

To prevent these operations from locking up the application, DineSmart executes them asynchronously using Java’s `CompletableFuture` class. This offloads time-consuming background tasks away from the **JavaFX Application Thread**, which remains dedicated to rendering the UI and handling user interactions.

---

### 6.2 Threading Approach
DineSmart uses `CompletableFuture.supplyAsync()` to execute long-running operations in the background. The core threading workflow is implemented in `DineSmartApp.java`:

1. **Background Task Execution:**
   * **API Search & Browsing:** Executes `api.searchByName(query.trim())` and `api.searchByCategory(category)` asynchronously.
   * **Recipe Import:** Retrieves complete recipe data via `api.lookupById()` and persists it via `recipeRepository.importRecipe()` inside a background thread.
   * **Historical Order Analysis:** Clicking *"Analyze historical orders (background)"* executes `orderRepository.analyzeHistoricalOrderLog()` in the background to compute sales statistics and order metrics.
2. **Completion Handling & Thread Synchronization:**
   * The `whenComplete()` completion handler determines whether the operation succeeded or threw an exception.
   * Once background processing finishes, `Platform.runLater()` is invoked to safely push interface updates back to the **JavaFX Application Thread**.

---

### 6.3 User Interface Responsiveness
To maintain UI responsiveness and prevent accidental duplicate submissions while asynchronous tasks run:

* **Visual Feedback & Controls:** The system provides real-time status messages (e.g., *"Searching TheMealDB..."*) and temporarily disables the action button triggering the operation (e.g., search, import, or analyze). Upon completion, the button is re-enabled.
* **Thread-Safe UI Modifications:** All updates to JavaFX components—including buttons, status labels, text areas, and recipe lists—are wrapped inside `Platform.runLater()` to strictly comply with JavaFX threading rules.
* **Non-Blocking User Workflow:** Because the main UI thread is never blocked, users can smoothly navigate and interact with other sections of DineSmart while background operations execute.

---

### 6.4 Error Handling & Completion Updates
DineSmart uses the `whenComplete()` callback to handle both successful completions and unexpected exceptions gracefully:

* **Successful Execution:** Updates the UI with parsed details or output summaries. For example, a successful recipe import reports the saved recipe name, API meal ID, category, and total saved ingredients.
* **Failure Execution:** Displays clear error notifications on the status label and output panel. Action buttons are re-enabled immediately to allow the user to retry the operation.
* **Network Fault Tolerance:** The `TheMealDbService.java` class sets connection and request timeouts to prevent network operations from hanging indefinitely if the external service is slow or unresponsive.

### 6.5 Code References

The following classes and methods demonstrate the concurrency implementation of DineSmart:

| Class/File | Method/Implementation | Purpose |
| :--- | :--- | :--- |
| `DineSmartApp.java` | `CompletableFuture.supplyAsync()`<br>`api.searchByName()` | Executes recipe searching in the background. |
| `DineSmartApp.java` | `CompletableFuture.supplyAsync()`<br>`api.searchByCategory()` | Executes category browsing in the background. |
| `DineSmartApp.java` | `CompletableFuture.supplyAsync()`<br>`recipeRepository.importRecipe()` | Executes recipe importing and database saving in the background. |
| `DineSmartApp.java` | `CompletableFuture.supplyAsync()`<br>`orderRepository.analyzeHistoricalOrderLog()` | Executes historical order analysis in the background. |
| `DineSmartApp.java` | `whenComplete()`<br>`Platform.runLater()` | Handles operation completion and safely updates the JavaFX interface. |
| `TheMealDbService.java` | `searchByName()`<br>`searchByCategory()`<br>`lookupById()` | Handles external API requests and recipe retrieval. |
| `OrderRepository.java` | `analyzeHistoricalOrderLog()` | Performs historical order aggregation and generates an analysis report. |
| `MealRecipeRepository.java` | `importRecipe()` | Saves imported recipe information and ingredient relationships transactionally in SQLite. |

#### Summary:

DineSmart implements asynchronous processing through Java's `CompletableFuture.supplyAsync()` and JavaFX's `Platform.runLater()`. These mechanisms allow time-consuming operations to execute in the background while keeping the main interface available for user interaction. By combining asynchronous execution, completion handling, and appropriate interface updates, the application provides a more responsive experience during API communication, recipe importing, and historical order analysis.

## 7. Data Mining and & Visualization Design

DineSmart performs data aggregation and frequency analysis to summarize restaurant operations and present useful information through its dashboard. It collects data from the database, groups related records, and calculates counts or quantities to identify trends and patterns.

The application uses three main data visualizations:

### 1. Menu Category Frequency – Bar Chart

We use the `countByItemType()` method in `MenuItemRepository.java` to count the number of menu items belonging to each category, such as Appetizers, Main Courses, Beverages, and Combos. We use SQL's `COUNT()` function together with `GROUP BY` to group and count the records by category. 

* Chart used: Bar Chart.
* Why chosen: A bar chart was chosen because the data consists of separate categories, such as Appetizer, Main Course, Beverage, and Combo. These categories are independent of one another, so comparing their frequencies                using bars is straightforward.
   * Each bar represents one menu category.
   *  The height of each bar indicates the number of menu items in that category.
   *  Users can quickly compare which category has the most and fewest menu items.
   *  Differences between categories are easy to recognize because the bars share the same measurement scale.
### 2. Hourly Order Frequency – Line Chart

We use the `getOrdersByHour()` method in `OrderRepository.java` to group restaurant orders according to the hour they were placed. This helps identify the busiest ordering hours. We also use `getPeakOrderingHour()` to determine which hour has the highest number of orders.

- Chart used: Line Chart.
- Why chosen: A line chart was chosen because the data represents order frequency across different hours of the day. Time follows a natural sequence, making it suitable for displaying changes from one hour to the next.
  - The horizontal axis represents the hours of the day.
  - The vertical axis represents the number of orders.
  - Connecting the data points helps users see whether order activity increases or decreases throughout the day.
  - Peaks and drops are easier to identify, helping restaurant staff recognize busy and slow periods.
### 3. Best-Selling Foods and Beverages – Bar Chart

We use the `findBestSellers()` method in `OrderRepository.java` to analyze completed orders and calculate the total quantities sold for each food and beverage item. It groups the ordered items by their names and ranks them according to their total quantities sold. This helps identify which food and beverage products are the best-selling and least-selling items, allowing restaurant management to understand customer preferences and make better decisions about menu planning and inventory.

- Chart used: Bar Chart.
- Why chosen: A bar chart was chosen because the application compares the quantities sold for different food and beverage items. Each menu item is a separate product, and the goal is to determine which products sell the most.
  - Each bar represents an individual menu item.
  - The height of the bar represents its total quantity sold in completed orders.
  - Different colors distinguish food items (green) from beverages (orange).
  - Users can easily compare sales performance and identify the best-selling and least-selling products.
## Summary

DineSmart uses bar charts when the objective is to compare quantities among different groups or products, and a line chart when the objective is to understand how values change over time. The selected chart types make the dashboard easier to interpret and help restaurant staff make informed decisions about menu management, inventory planning, and business operations.

