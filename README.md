<div align="center">

CPE 314 — Software Design

University of Cebu

Computer Engineering Department

# Restaurant Ordering & Inventory Insights

Project Design Document

</div>

<br>

<table>
  <tbody>
    <tr>
      <td valign="top" align="left"><strong>Group Name / No.:</strong></td>
      <td valign="top" align="left">Group 5</td>
    </tr>
    <tr>
      <td valign="top" align="left"><strong>Members:</strong></td>
      <td valign="top" align="left">Kiro Del Rosario - Team Leader</td>
    </tr>
    <tr>
      <td></td>
      <td valign="top" align="left">Jamaica Ambuhot - Developer 1</td>
    </tr>
    <tr>
      <td></td>
      <td valign="top" align="left">Janine Ebrado - Developer 2</td>
    </tr>
    <tr>
      <td></td>
      <td valign="top" align="left">Josiela Yap - UI (User Interface)</td>
    </tr>
    <tr>
      <td></td>
      <td valign="top" align="left">Vincent Hayag - DB Admin</td>
    </tr>
    <tr>
      <td></td>
      <td valign="top" align="left">Ben Justin Lajot - QA (Quality Assurance)</td>
    </tr>
    <tr>
      <td valign="top" align="left"><strong>Instructor:</strong></td>
      <td valign="top" align="left">Richyll Son</td>
    </tr>
    <tr>
      <td valign="top" align="left"><strong>Term:</strong></td>
      <td valign="top" align="left">First Semester, S.Y. 2026-2027</td>
    </tr>
    <tr>
      <td valign="top" align="left"><strong>Document Version / Date:</strong></td>
      <td valign="top" align="left">Ver 0.3 - November 03, 2026</td>
    </tr>
  </tbody>
</table>

<br>


## TABLE OF CONTENTS

Revision History -----------------------------------------------------------------------------------------------i<br>

**1. Introduction & Project Overview--------------------------------------------------------------------------1**

**2. Preliminary ER Sketch & Team Roles ---------------------------------------------------------------------2**<br>
&nbsp;&nbsp;&nbsp;&nbsp;2.1 Preliminary Entity-Relationship Sketch ----------------------------------------------------------------2<br>
&nbsp;&nbsp;&nbsp;&nbsp;2.2 Team Roles----------------------------------------------------------------------------------------------3<br>

**3. System Architecture & Object-Oriented Design ---------------------------------------------------------3**<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.1 Class Hierarchy -----------------------------------------------------------------------------------------3<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.2 Single Responsibility Principle (SRP)-------------------------------------------------------------------4<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.3 System Components -----------------------------------------------------------------------------------4<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.4 Design Rationale ---------------------------------------------------------------------------------------4<br>
&nbsp;&nbsp;&nbsp;&nbsp;3.5 Data Structure and Recursive Ingredient Calculation -------------------------------------------------5<br>


**4. Data Model & Database Design --------------------------------------------------------------------------6**<br>
&nbsp;&nbsp;&nbsp;&nbsp;4.1 Finalized Entity Relationship Sketch--------------------------------------------------------------------6<br>
&nbsp;&nbsp;&nbsp;&nbsp;4.2 Database Relationships and Cardinalities--------------------------------------------------------------7<br>
&nbsp;&nbsp;&nbsp;&nbsp;4.3 Actual SQLite Database Schema -----------------------------------------------------------------------8<br>
&nbsp;&nbsp;&nbsp;&nbsp;4.4 Database Design Explanation --------------------------------------------------------------------------9<br>

**5. API Integration Design ------------------------------------------------------------------------------------9**<br>
&nbsp;&nbsp;&nbsp;&nbsp;5.1 Public API Used -----------------------------------------------------------------------------------------9<br>
&nbsp;&nbsp;&nbsp;&nbsp;5.2 API Endpoints -------------------------------------------------------------------------------------------9<br>
&nbsp;&nbsp;&nbsp;&nbsp;5.3 JSON Response Structure-------------------------------------------------------------------------------9<br>

**6. Concurrency Design----------------------------------------------------------------------------------------10**<br>
&nbsp;&nbsp;&nbsp;&nbsp;6.1 Background Operations --------------------------------------------------------------------------------10<br>
&nbsp;&nbsp;&nbsp;&nbsp;6.2 Threading Approach ------------------------------------------------------------------------------------10<br>
&nbsp;&nbsp;&nbsp;&nbsp;6.3 Threading Process---------------------------------------------------------------------------------------10<br>
&nbsp;&nbsp;&nbsp;&nbsp;6.4 Maintaining UI Responsiveness -------------------------------------------------------------------------11<br>

**7. Data Mining & Visualization Design -----------------------------------------------------------------------11**<br>
&nbsp;&nbsp;&nbsp;&nbsp;7.1 Data Mining Process-------------------------------------------------------------------------------------11<br>
&nbsp;&nbsp;&nbsp;&nbsp;7.2 Data Aggregation Methods -----------------------------------------------------------------------------11<br>
&nbsp;&nbsp;&nbsp;&nbsp;7.3 Charts Used ----------------------------------------------------------------------------------------------12<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;7.3.1 Menu Category Frequency — Bar Chart------------------------------------------------------------12<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;7.3.2 Best-Selling Menu Items — Bar Chart --------------------------------------------------------------12<br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;7.3.3 Hourly Ordering Activity — Line Chart--------------------------------------------------------------13<br>
&nbsp;&nbsp;&nbsp;&nbsp;7.4 Purpose of Data Visualization----------------------------------------------------------------------------13<br>




### Revision History

**Table 1: Revision History**

<table>
  <thead>
    <tr>
      <th align="left">Version</th>
      <th align="left">Date</th>
      <th align="left">Author</th>
      <th align="left">Changes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td rowspan="2" valign="top" align="left">0.1</td>
      <td valign="top" align="left">October 20, 2026</td>
      <td valign="top" align="left">Group 5</td>
      <td valign="top" align="left">Initial draft.</td>
    </tr>
    <tr>
      <td valign="top" align="left">October 25, 2026</td>
      <td valign="top" align="left">
        <strong>Ambuhot</strong> – Introduction<br>
        <strong>Ebrado</strong> – ER Sketch<br>
        <strong>Yap</strong> – Feature Checklist<br>
        <strong>Del Rosario</strong> – Task Split<br>
        <strong>Hayag</strong> – The Public API<br>
        <strong>Lajot</strong> – Risk/Questions
      </td>
      <td valign="top" align="left">
        Completed the Week 8 project proposal, including the introduction, ER sketch, feature checklist, task split, public API, and risks/questions.
      </td>
    </tr>
    <tr>
      <td valign="top" align="left">0.2</td>
      <td valign="top" align="left">October 26, 2026</td>
      <td valign="top" align="left">
        <strong>Yap &amp; Hayag</strong> – Class Hierarchy Diagram/Description<br>
        <strong>Ambuhot</strong> – SRP Justification<br>
        <strong>Ebrado</strong> – Finalized ERD<br>
        <strong>Del Rosario &amp; Lajot</strong> – Schema Description
      </td>
      <td valign="top" align="left">
        Added Week 9 design and documentation sections, including the Class Hierarchy Diagram, SRP Justification, Finalized ERD, and Schema Description.
      </td>
    </tr>
    <tr>
      <td valign="top" align="left">0.3</td>
      <td valign="top" align="left">November 03, 2026</td>
      <td valign="top" align="left">
        <strong>Ebrado &amp; Ambuhot</strong><br>
        Section 5 — API Integration Design Written<br><br>
        <strong>Yap &amp; Hayag</strong><br>
        Section 6 — Concurrency Design Written<br><br>
        <strong>Del Rosario &amp; Lajot</strong><br>
        Section 7 — Data Mining &amp; Visualization Design Written
      </td>
      <td valign="top" align="left">
        Added Week 10 project design documentation, including Section 5 — API Integration Design, Section 6 — Concurrency Design, and Section 7 — Data Mining &amp; Visualization Design.
      </td>
    </tr>
  </tbody>
</table>





## 1. Introduction & Project Overview

DineSmart is a Java-based restaurant management system designed to help restaurant staff manage menu items, customer orders, table reservations, ingredients, and sales reports. It uses SQLite to store restaurant data and TheMealDB API to access recipes from different international cuisines. The system helps reduce manual work, improve order processing, and monitor ingredient availability. Its target users are restaurant owners, supervisors, cashiers, waiters, and kitchen staff.


## 2. Preliminary ER Sketch & Team Roles

### 2.1 Preliminary Entity-Relationship Sketch
<img width="5008" height="6688" alt="image" src="https://github.com/user-attachments/assets/619f8fd2-8468-4068-aa0c-9177366de30b" />
<p align="center">
  <em>Figure 1: Preliminary Entity-Relationship Sketch of DineSmart</em>
</p>

### 2.2 Team Roles

Table 2: Team Members and Responsibilities

| Member | Role |
|---|---|
| Kiro Del Rosario- Team Leader | API integration, authentication, access control, and system integration. |
| Jamaica Ambuhot- Developer 1 | Menu management, meal categories, recipe management, and menu recommendations. |
| Janine Ebrado- Developer 2 | SQLite database operations, order processing, and menu data storage. |
| Vincent Hayag- DB Admin | Kitchen queue, order history, inventory, and reservation management. |
| Josiela Yap- UI (User Interface) | JavaFX interface design, navigation, and image management. |
| Ben Justin Lajot- QA (Quality Assurance) | System testing, sales reporting, and data validation. |

## 3. System Architecture & Object-Oriented Design

### 3.1 Class Hierarchy
<img width="1888" height="968" alt="image" src="https://github.com/user-attachments/assets/9f020a50-a5be-4041-b834-44fdd35f60a8" />
<p align="center">
  <em>Figure 2: DineSmart Class Hierarchy</em>
</p>

### 3.2 Single Responsibility Principle (SRP)

Table 3: Classes and Their Responsibilities

| Class | Responsibility |
|---|---|
| MenuItem | Defines common menu item attributes and behavior. |
| Appetizer | Represents appetizer menu items. |
| MainCourse | Represents main course menu items. |
| Beverage | Represents beverage menu items. |

### 3.3 System Components

Table 4: DineSmart System Components and Responsibilities

| Package | Responsibility |
|---|---|
| model | Represents restaurant data and objects. |
| controller | Handles application operations and checks role permissions. |
| data | Manages SQLite database access and queries. |
| service | Handles recipes, ingredient calculations, staff permissions, and order queues. |
| dto | Represents data received from the external API. |
| ui | Displays the JavaFX interface and handles user interactions. |
| util | Provides shared utility functions, including money formatting. |

### 3.4 Design Rationale

DineSmart uses inheritance to allow different menu item categories to share common attributes and behavior. Each subclass is responsible for identifying its own item type, making the code easier to organize and maintain. The application also separates database operations, API requests, and business functions into different classes to reduce repeated code and improve maintainability. This structure supports the Single Responsibility Principle, although the main DineSmartApp class still handles several UI responsibilities.

### 3.5 Data Structures and Recursive Ingredient Calculation

DineSmart uses queues, stacks, and recursion to manage restaurant operations.

- **FIFO Queue:** KitchenQueue and PersistentKitchenQueue process kitchen orders in the order they were received.

- **LIFO Stack:** PendingOrderStack manages pending orders and allows the most recently added order to be removed first.

- **Recursive Ingredient Calculation:** RecursiveIngredientCalculator calculates the total ingredients required for multiple ordered menu items, including shared ingredients.

The recursive method processes one ordered menu item at a time and combines its ingredient requirements with the accumulated totals.

**Base Case:** The recursion stops when all ordered menu items have been processed.

**Recursive Case:** The method processes the current menu item's ingredients and calls itself to process the next ordered item.


## 4. Data Model & Database Design

### 4.1 Finalized Entity Relationship Sketch
<img width="5008" height="6688" alt="image" src="https://github.com/user-attachments/assets/4aedfc32-f623-4415-bb3d-8d927db62309" />
<p align="center">
  <em>Figure 3: Finalized Entity-Relationship Diagram of DineSmart</em>
</p>

### 4.2 Database Relationships and Cardinalities

Table 5: Database Relationships

| Parent Entity | Child Entity | Cardinality |
|---|---|---|
| STAFF_USERS | RESTAURANT_ORDERS | One to zero or many |
| RESTAURANT_TABLES | RESTAURANT_ORDERS | One to zero or many |
| RESTAURANT_TABLES | TABLE_RESERVATIONS | One to zero or many |
| RESTAURANT_ORDERS | ORDER_ITEMS | One to zero or many |
| MENU_ITEMS | ORDER_ITEMS | One to zero or many |
| MENU_ITEMS | MENU_PERIODS | One to zero or many |
| MENU_ITEMS | MENU_ITEM_INGREDIENTS | One to zero or many |
| INGREDIENTS | MENU_ITEM_INGREDIENTS | One to zero or many |
| RECIPE_CATALOG | RECIPE_INGREDIENTS | One to zero or many |
| INGREDIENTS | INGREDIENT_MOVEMENTS | One to zero or many |
| RESTAURANT_ORDERS | INGREDIENT_MOVEMENTS | One to zero or many |

The database uses one-to-many relationships between related tables. Each parent record can be associated with zero or many child records. Some foreign keys, such as restaurant_orders.created_by, restaurant_orders.table_id, table_reservations.table_id, and ingredient_movements.order_id, allow NULL values. This means these child records can exist without being linked to a parent record, while each child record can reference at most one parent.

The database also uses two junction tables to support many-to-many relationships:

- **ORDER_ITEMS** connects restaurant orders with menu items.
- **MENU_ITEM_INGREDIENTS** connects menu items with ingredients.


### 4.3 Actual SQLite Database Schema

Table 6: Actual SQLite Database Schema

| Table | Primary Key | Main Attributes and Foreign Keys |
|---|---|---|
| staff_users | staff_user_id | username, password_hash, password_salt, display_name, role, active |
| restaurant_tables | table_id | table_label, capacity, active |
| menu_items | menu_item_id | name, item_type, price, price_cents, base_prep_minutes, api_meal_id, image_path, is_sold_out, available_today, daily_menu_date, is_archived |
| restaurant_orders | order_id | table_id (FK), created_by (FK), ordered_at, status, payment_status, paid_at, service_status, served_at, customer_name |
| order_items | order_item_id | order_id (FK), menu_item_id (FK), quantity, unit_price_at_order, unit_price_cents |
| ingredients | ingredient_id | name, stock_quantity, stock_unit |
| menu_item_ingredients | menu_item_id, ingredient_id | Both PK/FK, quantity_required, measure |
| menu_periods | period_id | menu_item_id (FK), period_name, starts_on, ends_on, active |
| recipe_catalog | recipe_id | source, external_id, name, category, area, instructions, thumbnail_url, ingredients_text, created_at |
| recipe_ingredients | recipe_id, ingredient_name | recipe_id (FK), source_measure, quantity, unit, parse_status |
| table_reservations | reservation_id | table_id (FK), guest_name, guest_phone, party_size, reservation_at, table_label, notes, status, created_at |
| menu_recommendations | recommendation_id | suggested_item, reason, suggested_by, status, created_at |
| ingredient_movements | movement_id | ingredient_id (FK), order_id (FK), quantity_change, reason, created_at |

SQLite uses INTEGER, TEXT, and REAL data types in these tables. The database also includes constraints such as PRIMARY KEY, FOREIGN KEY, UNIQUE, NOT NULL, and CHECK to help maintain valid data.


### 4.4 Database Design Explanation

DineSmart uses a relational database to organize restaurant information and prevent unnecessary data duplication. Primary keys uniquely identify each record, while foreign keys connect related records. The database supports order processing, menu management, ingredient tracking, recipe storage, and reservation management.

The system also records inventory movements and uses ingredient requirements to calculate stock deductions when orders are processed.

## 5. API Integration Design

### 5.1 Public API Used

DineSmart uses TheMealDB API, a public API that provides recipes from different cuisines. It allows users to search for meals, browse recipes by category, and view ingredients, preparation instructions, and meal images.

API Website: [TheMealDB API Documentation](https://www.themealdb.com/api.php)


### 5.2 API Endpoints

Table 7: TheMealDB API Endpoints

| Endpoint | Purpose |
|---|---|
| `search.php?s=` | Searches for recipes by meal name. |
| `filter.php?c=` | Retrieves recipes based on a selected category. |
| `lookup.php?i=` | Retrieves complete recipe details using a meal ID. |

Base URL: https://www.themealdb.com/api/json/v1/1/

DineSmart maps the Appetizer category to TheMealDB's Starter category. For Main Course, the system 
combines ten categories: Beef, Chicken, Goat, Lamb, Miscellaneous, Pasta, Pork, Seafood, Vegan, and 
Vegetarian.

### 5.3 JSON Response Structure

TheMealDB API returns recipe information in JSON format. The response contains a meals array that includes the meal ID, name, category, country of origin, cooking instructions, image URL, ingredients, and measurements. DineSmart uses the MealDbMealDto class to extract this information and converts it into MealRecipe objects for use in the application.

## 6. Concurrency Design

### 6.1 Background Operations

DineSmart uses background threads to perform operations that may take time, such as retrieving recipes from TheMealDB, downloading meal images, and analyzing historical order records.

These operations run separately from the JavaFX user interface to prevent the application from freezing.

### 6.2 Threading Approach

Table 8: Concurrency Implementation

| Component | Purpose |
|---|---|
| ExecutorService | Provides background threads for tasks. |
| CompletableFuture | Runs asynchronous operations. |
| Platform.runLater() | Updates JavaFX interface components on the UI thread. |
| TheMealDbService | Retrieves recipe information from the API. |
| OrderReportRepository | Performs historical order analysis. |

### 6.3 Threading Process
<img width="1288" height="1688" alt="image" src="https://github.com/user-attachments/assets/be9517f7-1384-44e3-960a-3d650fc2e3ff" />
<p align="center">
  <em>Figure 4: Background Threading Process of DineSmart</em>
</p>

### 6.4 Maintaining UI Responsiveness

DineSmart uses background threads to perform long-running tasks without freezing the JavaFX user interface. The system uses CompletableFuture and ExecutorService to execute tasks separately, while Platform.runLater() safely updates the interface after the task is completed. This allows users to continue interacting with the application while background operations are running.

## 7. Data Mining & Visualization Design

### 7.1 Data Mining Process

DineSmart performs data mining through frequency analysis and data aggregation using records stored in SQLite. The system analyzes menu categories, completed orders, best-selling menu items, and ordering activity to help restaurant staff understand restaurant performance.

### 7.2 Data Aggregation Methods

Table 9: Data Mining and Visualization

| Analysis | Actual aggregation method | Visualization |
|---|---|---|
| Menu category frequency | Counts saved menu records in each category. | Bar Chart |
| Best-selling menu items | Calculates quantities sold from all completed orders. | Bar Chart |
| Hourly ordering activity | Counts historical non-cancelled orders by hour. | Line Chart |
| Sales performance | Calculates today's completed-order sales and order count. | Numerical Summary |
| Staff sales | Groups today's completed-order sales by staff member. | Numerical Summary |


### 7.3 Charts Used

#### 7.3.1 Menu Category Frequency — Bar Chart

The system counts saved menu items under the Appetizer, Main Course, and Beverage categories. A bar chart is used to compare the number of stored menu records in each category. The chart represents the menu catalog rather than the number of items currently available for ordering.

<img width="975" height="371" alt="image" src="https://github.com/user-attachments/assets/46d6bc5e-d5e7-47bf-a4c1-5754ffdffb27" />
<p align="center">
  <em>Figure 5: Example Menu Category Frequency Chart (Illustrative data only)</em>
</p>


#### 7.3.2 Best-Selling Menu Items — Bar Chart

DineSmart uses completed-order records to calculate the total quantity sold for each menu item. A bar chart is used to compare sales quantities and identify popular food and beverages.
<img width="975" height="312" alt="image" src="https://github.com/user-attachments/assets/e34d97d2-2f5f-486b-a9ce-426caa535c59" />
<p align="center">
  <em>Figure 6: Example Best-Selling Menu Items Chart (Illustrative data only)</em>
</p>

#### 7.3.3 Hourly Ordering Activity — Line Chart

The system groups non-cancelled restaurant orders according to the hour they were placed. A line chart is used because it clearly shows how order activity changes throughout the day. It helps identify busy hours and periods with fewer orders.
<img width="975" height="250" alt="image" src="https://github.com/user-attachments/assets/1326e906-151b-4f71-b3ed-c29ddce62368" />
<p align="center">
  <em>Figure 7: Example Hourly Ordering Activity Chart (Illustrative data only)</em>
</p>


### 7.4 Purpose of Data Visualization

DineSmart uses charts to present restaurant information in a simple and understandable way. These visualizations help restaurant owners and supervisors review menu category distribution, identify best-selling dishes, review sales performance, and recognize busy ordering hours. The reports support better decisions about menu planning, ingredient preparation, and daily restaurant operations.
