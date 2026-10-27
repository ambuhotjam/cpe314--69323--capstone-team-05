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
