# DineSmart — Week 09 Project Design Document Additions

**Group 5 | CPE 314 | Theme E: Restaurant Ordering & Inventory Insights**

Use this text to complete Sections 3 and 4 of the existing Project Design Document. Review it as a group and keep it synchronized with the code actually committed to the repository.

## 3. System Architecture & Object-Oriented Design

### 3.1 Class hierarchy

```text
                         <<abstract>>
                           MenuItem
              name, price, basePrepMinutes
                    getItemType()
                  /       |        \
                 /        |         \
          Appetizer   MainCourse   Beverage

Combo
  - name
  - components: List<Component>
  - getTotalPrice()  [recursive]
  - addItem(...) / addCombo(...)
```

`MenuItem` defines the common data and contract shared by menu products. `Appetizer`, `MainCourse`, and `Beverage` inherit the common state and provide their own item-type value. The application stores and processes instances through the shared `MenuItem` type; therefore, the selected subclass's overridden method is called at runtime.

### 3.2 Single Responsibility Principle (SRP)

- **MenuItem:** stores and validates common menu-item information. It does not connect to the database or manage screens.
- **Appetizer:** represents an appetizer and identifies its category.
- **MainCourse:** represents a main course and identifies its category.
- **Beverage:** represents a beverage and identifies its category.
- **Combo:** owns combo components and calculates the total price, including nested combos. It rejects invalid quantities and direct or indirect cycles.
- **RestaurantOrder:** stores the order identifier, timestamp, and lifecycle status; it enforces allowed pending-order transitions.
- **KitchenQueue:** maintains FIFO order-processing behavior only.
- **OrderHistoryStack:** tracks recent pending orders and cancels the latest still-pending order.
- **Database:** creates the SQLite schema and provides connections.
- **MenuItemRepository:** performs parameterized database persistence and retrieval for menu items.
- **DineSmartApp:** constructs the JavaFX window and connects the user's button action to the application services.

The design separates menu modeling, order workflow, persistence, and user-interface responsibilities. This makes classes easier to test and reduces the need to change unrelated code when one concern changes.

### 3.3 Data structures and recursion

`KitchenQueue` uses a FIFO queue: the first order enqueued is the first returned. `OrderHistoryStack` uses a stack to inspect the most recently recorded pending order first. `Combo.getTotalPrice()` is recursive: a menu-item component contributes its price multiplied by its quantity; a child-combo component calls the same calculation on that child. The base case is a combo with no components, which totals zero. The implementation also rejects a combo cycle and non-positive quantities.

## 4. Data Model & Database Design

### 4.1 Finalized ERD (text form)

```text
MENU_ITEMS 1 --------< ORDER_ITEMS >-------- 1 RESTAURANT_ORDERS
    |
    | 1
    +--------< MENU_ITEM_INGREDIENTS >-------- 1 INGREDIENTS

MENU_ITEMS 1 --------< COMBO_COMPONENTS >-------- 1 COMBOS
                              |
                              +---- optional CHILD_COMBO_ID ----> COMBOS
```

Cardinality notes:
- One restaurant order has many order items; each order item belongs to one order.
- One menu item can appear in many order items; each order item references one menu item.
- Menu items and ingredients form a many-to-many relationship through `menu_item_ingredients`.
- A combo has many components. Each combo component points to exactly one menu item or one child combo, enforced by a CHECK constraint.
- `combo_components.child_combo_id` self-references `combos` to represent nested combos. Application logic prevents cycles.

### 4.2 SQLite schema

The implementation's `Database.java` contains the authoritative `CREATE TABLE IF NOT EXISTS` statements for these seven tables:

1. `menu_items` — menu item name, type, price, preparation time, optional TheMealDB meal ID.
2. `restaurant_orders` — order timestamp and status.
3. `order_items` — order-to-menu-item line items, quantity, and price captured at order time.
4. `ingredients` — ingredient stock quantity and unit.
5. `menu_item_ingredients` — junction table linking menu items and ingredients, with required quantity.
6. `combos` — named meal combos.
7. `combo_components` — combo contents, quantity, and either a menu-item reference or nested-combo reference.

Foreign keys are enabled for each connection. CHECK constraints reject negative prices/stock, invalid order statuses, non-positive quantities, and combo components that reference both or neither target type. JDBC statements use `PreparedStatement` and bound parameters; the repository demonstrates both INSERT and SELECT against the actual SQLite database.

### 4.3 Week 09 implementation boundary

The Week 09 demo should show the JavaFX window, the working database button, a created SQLite file with the seven tables, successful parameterized INSERT/SELECT operations, and passing JUnit tests. Full TheMealDB import, background CSV processing, sales aggregation, charts, and final UI polish are planned for later milestone work and must not be reported as complete until implemented and tested.
