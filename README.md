

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


## 6. RISKS / OPEN QUESTIONS

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

