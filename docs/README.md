<div align="center">

CPE 314 — Software Design

University of Cebu

Computer Engineering Department

# Restaurant Ordering & Inventory Insights

Project Design Document

</div>

<br>

| | |
|---|---|
| **Group Name / No.:** | Group 5 |
| **Members:** | Kiro Del Rosario- Team Leader |
| | Jamaica Ambuhot- Developer 1 |
| | Janine Ebrado- Developer 2 |
| | Josiela Yap- UI (User Interface) |
| | Vincent Hayag- DB Admin |
| | Ben Justin Lajot- QA (Quality Assurance) |
| **Instructor:** | Richyll Son |
| **Term:** | First Semester, S.Y. 2026-2027 |
| **Document Version / Date:** | Ver 0.1 - October 20, 2026 |

<br>

## Table of Contents

```text
Revision History ......................................... 3
1. Introduction & Project Overview ....................... 4
2. Preliminary ER Sketch & Team Roles .................... 5
    Team Roles ........................................... 6
3. System Architecture & Object-Oriented Design .......... 7
    SRP Justification .................................... 7
4. Data Model & Database Design .......................... 7
    Schema Description ................................... 7
```

## Revision History

Table 1: Revision history

| Version | Date | Author | Changes |
|:---|:---|:---|:---|
| 0.1 | October 20, 2026<br>October 25, 2026 | Group 5<br>**Ambuhot** - Introduction<br>**Ebrado** - ER Sketch<br>**Yap** - Feature Checklist<br>**Del Rosario** - Task Split<br>**Hayag** - The Public API<br>**Lajot** - Risk/Questions | Initial draft<br><br>Completed the Week 8 project proposal, including the introduction, ER sketch, feature checklist, task split, public API, and risks/questions. |
| 0.2 | October 26, 2026 | **Yap & Hayag** - Class Hierarchy Diagram/Description<br>**Ambuhot** - SRP Justification<br>**Ebrado** - Finalized ERD<br>**Del Rosario & Lajot** - Schema Description | Added Week 9 design and documentation sections, including the Class Hierarchy Diagram, SRP Justification, Finalized ERD, and Schema Description. |
| 0.3 | | | Week 10 sections added |
| 1.0 | | | Final compilation and polish - Week 11 |


## Introduction & Project Overview
The Restaurant Ordering & Inventory Insights is a JavaFX desktop application designed for small restaurants and campus canteens that need an efficient way to manage orders and ingredient inventory. The application will manage menu items, nested meal combos, and the kitchen order queue while integrating with TheMealDB to import real recipe information. It will use SQLite to store records and generate charts showing best-selling menu items and peak ordering hours, helping staff improve food preparation and inventory management.

## Preliminary ER Sketch & Team Roles
<img width="4008" height="3168" alt="image" src="https://github.com/user-attachments/assets/3ec183e8-4a63-4e80-bec8-cbe5d00a00a3" />
<p align="center">
  <em>Figure 1: Restaurant Ordering & Inventory System Diagram</em>
</p>

## Team Roles

Table 1: Team members and proposed responsibilities

| Member | Role |
|---|---|
| Kiro Del Rosario - Team Leader | TheMealDB integration and background CSV-import plan. |
| Jamaica Ambuhot - Developer 1 | OOD hierarchy and recursive combo pricing plan. |
| Janine Ebrado - Developer 2 | ER sketch and SQLite/JDBC data-layer plan. |
| Vincent Hayag - DB Admin | Kitchen Queue, cancellation Stack and stock-flow plan. |
| Josiela Yap - UI (User Interface) | JavaFX screen sketch and event-handler plan. |
| Ben Justin Lajot - QA (Quality Assurance) | JUnit test cases, sales aggregation and chart plan. |

## System Architecture &amp; Object-Oriented Design
<img width="1536" height="1024" alt="w9 image" src="https://github.com/user-attachments/assets/3434b7a4-1d16-4221-a953-4759aa68da1a" />
<p align="center">
 <em>Figure 2: Class Diagram for Restaurant Menu Items</em>
 </p>
The class hierarchy diagram illustrates the inheritance relationships among the menu-related classes of
the Restaurant Ordering &amp; Inventory Insights application. It consists of one abstract superclass,
MenuItem, and four concrete subclasses: Appetizer, MainCourse, Beverage, and Combo. This structure
organizes menu items according to their categories while promoting code reusability and
maintainability.

1. MenuItem (Abstract Superclass)
The MenuItem class serves as the abstract superclass for all menu items in the application. It stores and
validates common menu-item information and provides a shared structure for its subclasses. This design
reduces code duplication and establishes a consistent way of representing menu items throughout the
application.

2. Appetizer (Concrete Subclass)
The Appetizer class extends the MenuItem class and represents appetizer items offered by the
restaurant. It inherits the common menu-item information and behavior defined by its superclass while
identifying items belonging to the appetizer category. This allows appetizer items to be managed
consistently with other menu categories.

3. MainCourse (Concrete Subclass)
The MainCourse class extends the MenuItem class and represents the main-course items available on
the restaurant&#39;s menu. It inherits the common menu-item information and behavior defined by its
superclass while distinguishing main-course items from appetizers, beverages, and combos.

4. Beverage (Concrete Subclass)
The Beverage class extends the MenuItem class and represents beverages offered by the restaurant. It
inherits the common menu-item information and behavior defined by its superclass while identifying
items belonging to the beverage category. This maintains a consistent structure for managing different
types of menu items.

5. Combo (Concrete Subclass)
The Combo class extends the MenuItem class and represents combinations of individual menu items or
other combos. It maintains combo components, recursively calculates the total price of nested combos,
validates component quantities, and prevents circular references. These responsibilities allow the
application to support nested combos while maintaining valid component relationships and accurate
price calculations.

The class hierarchy helps organize the different types of menu items in the system. The MenuItem class
serves as the parent class, while Appetizer, MainCourse, Beverage, and Combo represent the different
menu categories. This setup allows the classes to share common information instead of repeating the
same code. The Combo class can also handle combinations of menu items, including combos within
other combos, and calculate their total prices. Overall, this structure makes the menu easier to manage
and supports the system&#39;s ordering and pricing features.

## SRP Justification
* MenuItem: Stores and validates common menu-item information. It does not manage the user interface or database operations.
* Appetizer: Represents appetizer menu items and their category.
* MainCourse: Represents main-course menu items and their category.
* Beverage: Represents beverage menu items and their category.
* Combo: Manages combo components, calculates nested combo prices recursively, and prevents invalid quantities and circular references.
* RestaurantOrder: Stores the order ID, timestamp, and status and ensures that order-status transitions follow the defined rules.
* KitchenQueue: Manages FIFO (First-In, First-Out) processing of kitchen orders.
* OrderHistoryStack: Tracks recent orders and supports cancellation of the latest order that is still pending.
* Database: Manages SQLite database connections and initializes the database schema.
* MenuItemRepository: Handles saving, retrieving, and other database operations for menu items using parameterized JDBC statements.
* DineSmartApp: Builds the JavaFX interface, handles user interactions, and delegates operations to the appropriate application services and repositories.

Each class has a specific responsibility, separating menu management, combo pricing, order processing, database operations, and user-interface functions. This design reduces code duplication, improves testability, and makes the application easier to maintain. Changes to one part of the system can be made with less impact on other parts.
The planned design will also assign clear responsibilities for inventory management, TheMealDB integration, and sales reporting to maintain separation between different parts of the system.

## Schema Description

**Entity-Relationship Diagram Description**

The Entity-Relationship (ER) diagram illustrates the database structure of the **Restaurant Ordering & Inventory Insights** application. The schema is implemented in SQLite and accessed through JDBC using PreparedStatement. It consists of seven tables: MENU_ITEMS, INGREDIENTS, MENU_ITEM_INGREDIENTS, RESTAURANT_ORDERS, ORDER_ITEMS, COMBOS, and COMBO_COMPONENTS. They are linked through primary keys (PKs) and foreign keys (FKs) and fall into three groups: menu and recipe data, order data, and combo data.

**Table 1: Entities and their key Attributes**

| Table | Columns (type) | Purpose |
|:---|:---|:---|
| MENU_ITEMS | menu_item_id (INTEGER, PK)<br>name (TEXT)<br>item_type (TEXT)<br>price (REAL)<br>base_prep_minutes (INTEGER)<br>api_meal_id (TEXT) | Stores each dish or drink on the menu. item_type holds the category (Appetizer, MainCourse or Beverage), base_prep_minutes is the preparation estimate, and api_meal_id links an imported item to its TheMealDB recipe. |
| INGREDIENTS | ingredient_id (INTEGER, PK)<br>name (TEXT)<br>stock_quantity (REAL)<br>stock_unit (TEXT) | Keeps the ingredient inventory: what is in stock and in which unit. |
| MENU_ITEM_INGREDIENTS | menu_item_id (INTEGER, PK, FK)<br>ingredient_id (INTEGER, PK, FK)<br>quantity_required (REAL)<br>measure (TEXT) | Junction table for the recipe of each menu item. quantity_required is the amount used per serving, and measure keeps the measure text from the recipe. |
| RESTAURANT_ORDERS | order_id (TEXT, PK)<br>ordered_at (TEXT)<br>status (TEXT) | Stores one record per customer order with its timestamp and current status. |
| ORDER_ITEMS | order_item_id (INTEGER, PK)<br>order_id (TEXT, FK)<br>menu_item_id (INTEGER, FK)<br>quantity (INTEGER)<br>unit_price_at_order (REAL) | Stores the lines of an order: which menu item, how many, and the unit price when the order was placed. |
| COMBOS | combo_id (INTEGER, PK)<br>name (TEXT) | Stores each meal combo and its name. |
| COMBO_COMPONENTS | component_id (INTEGER, PK)<br>combo_id (INTEGER, FK)<br>menu_item_id (INTEGER, FK)<br>child_combo_id (INTEGER, FK)<br>quantity (INTEGER) | Stores the parts of a combo. Each row points to either a regular menu item or another combo, which allows nesting. |

**Table 2: Relationships and what they mean**

| Relationship | Cardinality | Description |
|---|---|---|
| `MENU_ITEMS` and `INGREDIENTS` through `MENU_ITEM_INGREDIENTS` | Many-to-many | A menu item uses many ingredients, and an ingredient appears in many menu items. The composite key (`menu_item_id`, `ingredient_id`) prevents duplicate recipe lines. |
| `RESTAURANT_ORDERS` contains `ORDER_ITEMS` | One-to-many | One order has one or more order items. |
| `MENU_ITEMS` ordered as `ORDER_ITEMS` | One-to-many | One menu item can appear in many order items, so orders and menu items are many-to-many through `ORDER_ITEMS`. |
| `COMBOS` contains `COMBO_COMPONENTS` | One-to-many | One combo has one or more components (`combo_id`). |
| `MENU_ITEMS` included as `COMBO_COMPONENTS` | One-to-many (optional) | A component may reference a regular menu item through `menu_item_id`. |
| `COMBOS` nested as `COMBO_COMPONENTS` | One-to-many (optional) | A component may instead reference another combo through `child_combo_id`, which allows combos inside combos at any depth. |

**Design Rules and Constraints**

Each `COMBO_COMPONENTS` row references exactly one of `menu_item_id` or `child_combo_id`; the other must be `NULL`. A foreign key cannot detect circular combo references, so the application rejects cycles when a component is added and totals the price of nested combos recursively. Quantities in `COMBO_COMPONENTS` and `ORDER_ITEMS` must be positive. Invalid quantities are rejected before they are saved.

`ORDER_ITEMS` stores `unit_price_at_order` so that past orders and sales reports stay correct after a menu price changes. The status in `RESTAURANT_ORDERS` tracks whether an order is pending, completed, or cancelled. It supports cancellation of the latest pending order, and cancelled orders are excluded from sales results. `ordered_at` is stored as `TEXT` in a date-time format so orders can be grouped by hour.

`MENU_ITEMS.api_meal_id` and the `MENU_ITEM_INGREDIENTS` rows are filled when staff review and import a meal from TheMealDB API. They remain empty or are entered manually for items created by hand. Before an order is accepted, the required stock is checked against `INGREDIENTS.stock_quantity`. The order is rejected when stock is insufficient.

Overall, this database design supports menu management, order processing, ingredient inventory tracking, recipe integration through the TheMealDB API, and sales analysis based on order timestamps. Sales queries join `ORDER_ITEMS`, `RESTAURANT_ORDERS`, and `MENU_ITEMS` to total completed item quantities and count completed orders by hour. It provides the relational foundation for the application's Java implementation using JDBC and SQLite.

