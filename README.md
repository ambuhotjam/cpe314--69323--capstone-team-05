

| **Group Name / No.:** | Group 5 |
|---|---|
| **Members:** | Kiro Del Rosario - Team Leader <br> Jamaica Ambuhot - Developer 1 <br> Janine Ebrado - Developer 2 <br> Josiela Yap - User Interface (UI) <br> Vincent Hayag - DB Admin <br> Ben Justin Lajot - Quality Assurance (QA) |
| **Repository URL:** | https://github.com/ambuhotjam/cpe314--69323--capstone-team-05.git |
| **Theme:** | Restaurant Ordering & Inventory Insights |
| **API You Will Use:** | TheMealDB — https://www.themealdb.com/api/json/v1/1/ |






## 1. ONE-PARAGRAPH PITCH
DineSmart is a Java-based restaurant management system designed to help restaurant staff manage menu items, customer orders, table reservations, ingredients, and sales reports. It uses SQLite to store restaurant data and TheMealDB API to access recipes from different international cuisines. The system helps reduce manual work, improve order processing, and monitor ingredient availability. Its target users are restaurant owners, supervisors, cashiers, waiters, and kitchen staff.


## 2. ENTITY-RELATIONSHIP SKETCH
<img width="5008" height="6688" alt="image" src="https://github.com/user-attachments/assets/1f3d8b66-ea66-476e-a2de-1dc5104787af" />
<p align="center">
  <em>Figure 1:Preliminary Entity-Relationship Sketch of DineSmart</em>
</p>


## 3. FEATURE CHECKLIST

**Table 1: DineSmart Planned Features and Corresponding Requirements**

| Planned Feature | Requirement(s) It Satisfies |
|---|---|
| Menu Management | CRUD Operations, OOP, Inheritance, and SRP |
| Customer Ordering and Table Reservations | SQLite Database and Relational Data Management |
| Kitchen Queue and Pending Orders | FIFO Queue and LIFO Stack |
| Recursive Ingredient Calculator | Recursion and Ingredient Calculation |
| Inventory Management | CRUD Operations and SQLite Persistence |
| TheMealDB Recipe Integration | Public API Integration and JSON Processing |
| Staff Authentication and Access Control | Authentication, Authorization, and Role-Based Permissions |
| Background Recipe and Image Loading | Concurrency and Multithreading |
| Sales Reports and Analytics | Data Mining, Aggregation, and Data Visualization |
| JavaFX User Interface | Graphical User Interface and Event Handling |



## 4. TASK SPLIT

**Table 2: Team Members and Responsibilities**

| Member | Role |
|---|---|
| Kiro Del Rosario - Team Leader | API integration, authentication, access control, and system integration. |
| Jamaica Ambuhot - Developer 1 | Menu management, meal categories, recipe management, and menu recommendations. |
| Janine Ebrado - Developer 2 | SQLite database operations, order processing, and menu data storage. |
| Vincent Hayag - DB Admin | Kitchen queue, order history, inventory, and reservation management. |
| Josiela Yap - UI (User Interface) | JavaFX interface design, navigation, and image management. |
| Ben Justin Lajot - QA (Quality Assurance) | System testing, sales reporting, and data validation. |


## 5. PUBLIC API YOU PLAN TO USE

**API Name:** TheMealDB

**Base URL:** https://www.themealdb.com/api/json/v1/1/

TheMealDB is a public API that provides recipes from different cuisines, including meal names, categories, ingredients, cooking instructions, and images. DineSmart uses this API to search for meals, browse recipes by category, and retrieve recipe details for its menu management system.



## 6. Risks / Open Questions

**Table 3: DineSmart Project Risks and Open Questions**

| No. | Risks | Open Questions |
|---|---|---|
| 1 | API connection failures and unsuccessful recipe requests. | How can we retrieve recipes from TheMealDB and handle errors when the internet connection is unavailable? |
| 2 | Incorrect or missing records in the SQLite database. | How can we ensure that customer orders, menu items, reservations, and inventory records are stored and updated correctly? |
| 3 | Incorrect ingredient calculations, especially for shared ingredients. | How can we accurately calculate the total ingredients required for multiple orders? |
| 4 | Improper processing of kitchen and pending orders. | How can we implement FIFO queues and LIFO stacks to manage orders efficiently? |
| 5 | JavaFX interface freezing during long-running operations. | How can we keep the interface responsive while retrieving recipes, loading images, and generating reports? |
| 6 | Undetected errors in system functions and database operations. | How can we verify that the system handles invalid inputs, inventory calculations, and database operations correctly? |
| 7 | Unauthorized access to restricted system features. | How can we ensure that staff members can access only the features allowed for their assigned roles? |
| 8 | Inaccurate ingredient stock levels after processing orders. | How can we automatically update ingredient stocks when customer orders are processed? |
| 9 | Inaccurate analysis or presentation of restaurant sales and order records. | How can we analyze restaurant orders and display useful information through charts and sales reports? |
| 10 | Integration errors between system components. | How can we ensure that the JavaFX interface, SQLite database, and restaurant management features work together correctly? |


