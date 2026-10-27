## ONE-PARAGRAPH PITCH

The Restaurant Ordering & Inventory Insights is a JavaFX desktop application designed for small restaurants and campus canteens that need an efficient way to manage orders and ingredient inventory. The application will manage menu items, nested meal combos, and the kitchen order queue while integrating with TheMealDB to import real recipe information. It will use SQLite to store records and generate charts showing best-selling menu items and peak ordering hours, helping staff improve food preparation and inventory management.



## ENTITY-RELATIONSHIP SKETCH

## Table 1: Entities and Their Key Attributes

| Entity | Key attributes and other fields |
|---|---|
| **MenuItem** | **Keys:** `menu_item_id` PK  <br> **Other fields:** `name`, `item_type`, `price`, `base_prep_minutes`, `api_meal_id` |
| **RestaurantOrder** | **Keys:** `order_id` PK  <br> **Other fields:** `ordered_at`, `status` |
| **OrderItem** | **Keys:** `order_item_id` PK; `order_id` FK; `menu_item_id` FK  <br> **Other fields:** `quantity`, `unit_price_at_order` |
| **Ingredient** | **Keys:** `ingredient_id` PK  <br> **Other fields:** `name`, `stock_quantity`, `stock_unit` |
| **MenuItemIngredient** | **Keys:** `menu_item_id` PK/FK; `ingredient_id` PK/FK  <br> **Other fields:** `quantity_required` |
| **Combo** | **Keys:** `combo_id` PK  <br> **Other fields:** `name` |
| **ComboComponent** | **Keys:** `component_id` PK; `combo_id` FK; `menu_item_id` FK (nullable); `child_combo_id` FK (nullable)  <br> **Other fields:** `quantity` |



## Table 2: Relationships and what they mean

| Relationship | Type | Meaning |
| :--- | :---: | :--- |
| RestaurantOrder to OrderItem | 1:M| One order contains several ordered-item records. |
| MenuItem to OrderItem | 1:M | One menu item can appear in several orders. |
| MenuItem to MenuItemIngredient | 1:M| One menu item can require several ingredients. |
| Ingredient to MenuItemIngredient | 1:M | One ingredient can be used by several menu items. |
| Combo to ComboComponent | 1:M | One combo contains several component records |
| MenuItem to ComboComponent | 1:M | One menu item can be used in several combo components |
| Combo to ComboComponent (as child) | 1:M | One child combo can be used in several parent-combo components |

1:M means one-to-many. OrderItem links orders with menu items; MenuItemIngredient links menu items with ingredients.

Combo rule: each component points to exactly one menu item OR one child combo. Quantities must be positive. A combo must not contain itself, directly or indirectly.


## 3. Feature Checklist

Check off which mandatory requirement each planned feature will satisfy:

| Planned Feature | Requirement(s) It Satisfies |
| :--- | :--- |
| MenuItem abstract class with Appetizer, MainCourse and Beverage subclasses; different preparation estimates; separate services. | Object-Oriented Design |
| FIFO kitchen Queue, latest-pending-order Stack, and recursive total for combos nested at any depth; reject cycles. | Data Structure / Recursion |
| At least 6 JUnit 5 tests: totals, invalid quantity, FIFO, cancellation, nested combos and insufficient stock. | Automated Testing |
| Related SQLite tables; JDBC PreparedStatement CRUD and JOIN queries. | Data Modeling + Database |
| Search TheMealDB and save selected meal names and real ingredient lists after staff review. | API Consumption |
| JavaFX Place Order, Cancel Latest Pending Order, Search Recipe and Import History controls with handlers. | Event-Driven GUI |
| Import a large historical order CSV in Task.call(); report progress and update UI on the JavaFX thread. | Concurrency |
| Aggregate completed item quantities and completed order counts by hour; exclude cancelled orders. | Data Mining |
| Labeled bar chart of item quantities sold and line chart of orders by hour. | Data Visualization |



### 4. Task Split

| Member | Primary Responsibility (this milestone) |
| :--- | :--- |
| Jamaica Ambuhot | OOD hierarchy and recursive combo pricing plan. |
| Janine Ebrado | ER sketch and SQLite/JDBC data-layer plan. |
| Josiela Yap | JavaFX screen sketch and event-handler plan. |
| Vincent Hayag | Kitchen Queue, cancellation Stack and stock-flow plan. |
| Kiro Del Rosario | TheMealDB integration and background CSV-import plan. |
| Ben Justin Lajot | JUnit test cases, sales aggregation and chart plan. |


### 5. Public API You Plan to Use

TheMealDB provides real meal names and ingredient lists for staff to review and import into the restaurant menu.


### 6. Risks / Open Questions

| RISKS | QUESTIONS |
|---|---|
| TheMealDB might not provide complete recipe information, or the internet might not work. | What should we do if recipe information is missing or is the internet unavailable? Can we use saved recipe data? |
| The inventory might become incorrect if an order is cancelled after food preparation starts. | Should we allow order cancellation after food preparation has started? |
| The application might freeze when processing many order records. | How can we prevent the application from freezing when processing order history? | 
