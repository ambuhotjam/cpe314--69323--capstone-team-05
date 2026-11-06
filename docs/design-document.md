# 3. System Architecture & Object-Oriented Design

## 3.1 Class Hierarchy


<img width="757" height="350" alt="image" src="https://github.com/user-attachments/assets/950d1640-e92c-4683-9013-d10489c68c36" />


The class hierarchy diagram illustrates the inheritance relationships among the menu-related classes of the Restaurant Ordering & Inventory Insights application. It consists of one abstract superclass, `MenuItem`, and four concrete subclasses: `Appetizer`, `MainCourse`, `Beverage`, and `Combo`.

This structure organizes menu items according to their categories while promoting code reusability and maintainability.

### 3.1.1 MenuItem (Abstract Superclass)

The `MenuItem` class serves as the abstract superclass for all menu items in the application. It stores and validates common menu-item information and provides a shared structure for its subclasses. This design reduces code duplication and establishes a consistent way of representing menu items throughout the application.

### 3.1.2 Appetizer (Concrete Subclass)

The `Appetizer` class extends the `MenuItem` class and represents appetizer items offered by the restaurant. It inherits the common menu-item information and behavior defined by its superclass while identifying items belonging to the appetizer category.

### 3.1.3 MainCourse (Concrete Subclass)

The `MainCourse` class extends the `MenuItem` class and represents the main-course items available on the restaurant's menu. It inherits the common menu-item information and behavior defined by its superclass while distinguishing main-course items from other menu categories.

### 3.1.4 Beverage (Concrete Subclass)

The `Beverage` class extends the `MenuItem` class and represents beverages offered by the restaurant. It inherits the common menu-item information and behavior defined by its superclass while identifying items belonging to the beverage category.

### 3.1.5 Combo (Concrete Subclass)

The `Combo` class extends the `MenuItem` class and represents combinations of individual menu items or other combos. It maintains combo components, recursively calculates the total price of nested combos, validates component quantities, and prevents circular references.

## 3.2 Single Responsibility Principle (SRP) Justification

The application follows the Single Responsibility Principle by assigning specific responsibilities to its classes:

- **MenuItem:** Stores and validates common menu-item information. It does not manage the user interface or database operations.
- **Appetizer:** Represents appetizer menu items and their category.
- **MainCourse:** Represents main-course menu items and their category.
- **Beverage:** Represents beverage menu items and their category.
- **Combo:** Manages combo components, calculates nested combo prices recursively, and prevents invalid quantities and circular references.
- **RestaurantOrder:** Stores the order ID, timestamp, and status and ensures that order-status transitions follow the defined rules.
- **KitchenQueue:** Manages FIFO (First-In, First-Out) processing of kitchen orders.
- **OrderHistoryStack:** Tracks recent orders and supports cancellation of the latest order that is still pending.
- **Database:** Manages SQLite database connections and initializes the database schema.
- **MenuItemRepository:** Handles saving, retrieving, and other database operations for menu items using parameterized JDBC statements.
- **DineSmartApp:** Builds the JavaFX interface, handles user interactions, and delegates operations to the appropriate application services and repositories.

Each class has a specific responsibility, separating menu management, combo pricing, order processing, database operations, and user-interface functions. This design improves organization, reduces code duplication, and makes individual classes easier to maintain and test.

---

# 4. Data Model & Database Design

## 4.1 Finalized Entity-Relationship Diagram (ERD)

**Figure 3: Finalized Entity-Relationship Diagram for DineSmart**

[Insert the updated eight-table finalized ERD here.]

The Entity-Relationship Diagram (ERD) illustrates the database structure of the Restaurant Ordering & Inventory Insights application. The database is implemented using SQLite and accessed through Java Database Connectivity (JDBC).

The database consists of eight tables: `MENU_ITEMS`, `INGREDIENTS`, `MENU_ITEM_INGREDIENTS`, `RESTAURANT_ORDERS`, `ORDER_ITEMS`, `ORDER_COMBO_ITEMS`, `COMBOS`, and `COMBO_COMPONENTS`.

These tables support menu management, ingredient inventory, customer orders, combo ordering, and recipe relationships. Primary keys and foreign keys establish relationships between records and help maintain data integrity.

## 4.2 Entity and Attribute Definitions

**Table 3: Database Entities and Their Key Attributes**

| Table | Key Attributes | Purpose |
|---|---|---|
| MENU_ITEMS | menu_item_id (INTEGER, PK), name (TEXT), item_type (TEXT), price (REAL), base_prep_minutes (INTEGER), api_meal_id (TEXT) | Stores restaurant menu items, categories, prices, preparation estimates, and optional TheMealDB recipe identifiers. |
| INGREDIENTS | ingredient_id (INTEGER, PK), name (TEXT), stock_quantity (REAL), stock_unit (TEXT) | Stores ingredient names, available stock quantities, and measurement units. |
| MENU_ITEM_INGREDIENTS | menu_item_id (INTEGER, PK, FK), ingredient_id (INTEGER, PK, FK), quantity_required (REAL), measure (TEXT) | Connects menu items to their required ingredients and records the quantities used. |
| RESTAURANT_ORDERS | order_id (TEXT, PK), ordered_at (TEXT), status (TEXT), customer_name (TEXT) | Stores customer orders, timestamps, customer names, and order statuses. |
| ORDER_ITEMS | order_item_id (INTEGER, PK), order_id (TEXT, FK), menu_item_id (INTEGER, FK), quantity (INTEGER), unit_price_at_order (REAL) | Stores individual menu-item entries belonging to customer orders, including their quantities and recorded prices. |
| ORDER_COMBO_ITEMS | order_combo_item_id (INTEGER, PK), order_id (TEXT, FK), combo_id (INTEGER, FK), quantity (INTEGER), unit_price_at_order (REAL) | Stores combo entries included in customer orders, including their quantities and recorded prices. |
| COMBOS | combo_id (INTEGER, PK), name (TEXT), discount_percent (REAL) | Stores meal combo information, including the combo name and applicable discount percentage. |
| COMBO_COMPONENTS | component_id (INTEGER, PK), combo_id (INTEGER, FK), menu_item_id (INTEGER, FK, nullable), child_combo_id (INTEGER, FK, nullable), quantity (INTEGER) | Stores the individual menu items or nested combos that make up a combo. |

## 4.3 Primary Keys and Foreign Keys

Primary keys (PKs) uniquely identify records in each database table. Foreign keys (FKs) establish relationships between related tables and help maintain referential integrity.

The `MENU_ITEM_INGREDIENTS` table uses a composite primary key consisting of `menu_item_id` and `ingredient_id`. This prevents the same ingredient from being entered more than once for the same menu item.

The `COMBO_COMPONENTS` table references either a regular menu item or another combo. Its `menu_item_id` and `child_combo_id` fields are nullable, but exactly one must contain a value for each component record.

## 4.4 Database Relationships

**Table 4: Database Relationships and Their Descriptions**

| Relationship | Cardinality | Description |
|---|---|---|
| MENU_ITEMS and INGREDIENTS through MENU_ITEM_INGREDIENTS | Many-to-many | A menu item may require multiple ingredients, and an ingredient may be used by multiple menu items. |
| RESTAURANT_ORDERS and ORDER_ITEMS | One-to-many | One customer order can contain multiple individual menu-item entries. |
| MENU_ITEMS and ORDER_ITEMS | One-to-many | A menu item can appear in multiple order-item records. |
| RESTAURANT_ORDERS and ORDER_COMBO_ITEMS | One-to-many | One customer order can contain multiple combo entries. |
| COMBOS and ORDER_COMBO_ITEMS | One-to-many | A combo can be included in multiple customer orders. |
| COMBOS and COMBO_COMPONENTS | One-to-many | One combo can contain multiple components. |
| MENU_ITEMS and COMBO_COMPONENTS | One-to-many, optional | A combo component may reference a regular menu item through `menu_item_id`. |
| COMBOS and COMBO_COMPONENTS | One-to-many, optional self-reference | A combo component may reference another combo through `child_combo_id`, allowing nested combos. |

## 4.5 Design Rules and Constraints

The database applies constraints to maintain valid and consistent records.

- Menu-item names are required and unique.
- Menu-item prices and preparation times cannot be negative.
- Ingredient stock quantities cannot be negative.
- Required ingredient quantities must be positive.
- Order-item and combo-item quantities must be positive.
- Recorded order prices cannot be negative.
- Combo discounts must be at least zero and less than 100 percent.
- Each `COMBO_COMPONENTS` record must reference exactly one of `menu_item_id` or `child_combo_id`.
- Foreign-key enforcement is enabled when a database connection is established.

The application also validates combo components and prevents circular combo references. Nested combo prices are calculated recursively.

The `ORDER_ITEMS` and `ORDER_COMBO_ITEMS` tables store `unit_price_at_order`. This preserves the price recorded when an order is placed, even if the current menu or combo price changes later.

The `RESTAURANT_ORDERS` table stores the order timestamp and status. The application uses these records to support order processing and sales-related queries.

The database structure provides the relational foundation for the application's menu management, ingredient records, order processing, combo management, and reporting operations.
