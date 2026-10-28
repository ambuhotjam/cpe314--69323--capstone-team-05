## ONE-PARAGRAPH PITCH

The Restaurant Ordering & Inventory Insights is a JavaFX desktop application designed for small restaurants and campus canteens that need an efficient way to manage orders and ingredient inventory. The application will manage menu items, nested meal combos, and the kitchen order queue while integrating with TheMealDB to import real recipe information. It will use SQLite to store records and generate charts showing best-selling menu items and peak ordering hours, helping staff improve food preparation and inventory management.



## ENTITY-RELATIONSHIP SKETCH


<img width="4008" height="3168" alt="image" src="https://github.com/user-attachments/assets/d84a0066-fac9-4fba-89e4-9881149f5ac2" />
<p align="center">
  <em>Figure 1: Restaurant Ordering & Inventory System Diagram</em>
</p>




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



## 4. Task Split

| Member | Primary Responsibility |
|---|---|
| Kiro Del Rosario - Team Leader | TheMealDB integration and background CSV-import plan. |
| Jamaica Ambuhot - Developer 1 | OOD hierarchy and recursive combo pricing plan. |
| Janine Ebrado - Developer 2 | ER sketch and SQLite/JDBC data-layer plan. |
| Vincent Hayag - DB Admin | Kitchen Queue, cancellation Stack and stock-flow plan. |
| Josiela Yap - UI (User Interface) | JavaFX screen sketch and event-handler plan. |
| Ben Justin Lajot - QA (Quality Assurance) | JUnit test cases, sales aggregation and chart plan. |

### 5. Public API You Plan to Use

TheMealDB (https://www.themealdb.com/api.php): a free recipe API that provides meal names,
categories, cooking instructions, images, and ingredient lists with measures. We will use the shared free
test key “1” (no signup) to seed our menu items and their ingredients.


### 6. Risks / Open Questions

| No. | Risks | Questions |
| --- | --- | --- |
| 1 | TheMealDB API failure | What will happen if TheMealDB API is unavailable? |
| 2 | Internet connection failure | Can the system still work without internet? |
| 3 | Incorrect inventory updates | How will you prevent incorrect ingredient stock quantities? |
| 4 | Insufficient ingredients | What happens if there is not enough stock for an order? |
| 5 | Circular combo references | How will you prevent a combo from containing itself indirectly? |
| 6 | Incorrect combo pricing | How will you calculate the price of nested combos accurately? |
| 7 | Database corruption | What will you do if the SQLite database becomes corrupted? |
| 8 | Data loss | How will you back up and restore restaurant records? |
| 9 | Invalid user input | How will the system handle negative or zero quantities? |
| 10 | Incorrect sales reports | How will you ensure that sales charts display accurate results? |
| 11 | Cancelled orders | How will you ensure cancelled orders are excluded from sales reports? |
| 12 | Menu price changes | What happens to previous orders when a menu item's price changes? |
| 13 | Duplicate recipe imports | How will you prevent the same recipe from being imported more than once? |
| 14 | Application crash | What happens if the application crashes while saving an order? |
| 15 | Incorrect order processing | How will you ensure that kitchen orders follow FIFO (First-In, First-Out)? |
| 16 | Incorrect order cancellation | How will you prevent completed orders from being cancelled accidentally? |
| 17 | Database query errors | How will you prevent invalid or unsafe database operations? |
| 18 | Testing failures | How will you verify that each feature works correctly before deployment? |
