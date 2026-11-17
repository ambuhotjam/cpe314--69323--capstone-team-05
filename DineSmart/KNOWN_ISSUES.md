# Known Issues and Remaining Work

**CPE 314 · Section 3B · Team 05 · Milestone 2 (Week 10)**

This list is intentionally explicit. It separates implemented fixes from work that still needs a demonstration or a later refactor.

## Implemented in this update

- Added `restaurant_tables` and foreign-key links from reservations and orders.
- Added the `ingredient_movements.order_id` foreign key.
- Added integer-cent columns for menu prices and order-time prices. Legacy decimal columns remain for migration compatibility.
- Moved order price totals and sales aggregation to integer cents.
- Made order cancellation restore ingredient stock and log `ORDER_CANCEL_RESTORE`.
- Added `RecursiveIngredientCalculator`: order lines are processed recursively, shared measurable ingredients are aggregated, and stock is validated/deducted once per ingredient within the order transaction.
- Added unit and SQLite integration tests for recursion, shared ingredient totals, insufficient combined stock, and transaction rollback.
- Added role-aware controller methods and denial tests for waiter, cashier, and kitchen actions.
- Extracted read-only sales, cashier, best-seller, and hourly aggregation queries into `OrderReportRepository`.
- Added TheMealDB DTO-to-domain mapping and local HTTP-server tests for success, empty results, bad status, timeout, and malformed JSON.
- Added `orders.created_by`, a signed-in user's "My orders today" query, and daily sales grouped by staff.
- Removed the bundled-meal feature from the active schema, repositories, and ERD. Existing historical bundle order lines are preserved as archived menu-item order lines during migration.
- Added the 13-table ERD, consolidated design document, README, and requirement-to-code map.

## Still to verify or complete

1. **Full view extraction:** `ui/DineSmartApp.java` is still the main JavaFX screen/composition class and remains large. Controllers now own role checks and selected application actions, but the separate `LoginView`, `HomeView`, `MenuView`, `OrderView`, `InventoryView`, `ReservationView`, and `ReportsView` extraction is not complete.
2. **Staff-management verification:** The supervisor/admin staff page is now present and calls role-protected `AuthController` methods. Demonstrate create, disable, enable, and password reset with a test account.
3. **Restaurant table administration and table map:** Table rows are created from labels entered for reservations and orders. A dedicated screen to configure table capacity and active/inactive status, and a visual table map for the cashier, are not yet present. Capacity defaults to `0` (not specified) and is not currently enforced.
4. **Permission coverage:** The role policy and order/staff-controller tests cover key cross-role actions. Add UI/controller tests for every menu, inventory, reservation, and report action as those screens are extracted.
5. **End-to-end demo:** Confirm TheMealDB connectivity, local photo storage, JavaFX responsiveness, staff sales, table assignment, and cancellation restoration in the actual desktop environment.
6. **Build verification:** Run `mvn clean test` from a fresh clone and confirm the exact test count and result before submission. This editing environment did not have Maven available, so no fresh Maven build result is claimed here.

## Data migration notes

- On first startup after this update, `Database.initialize()` adds cents and table columns to older databases and copies the existing decimal values into the integer-cent columns.
- The old decimal money columns are retained as compatibility mirrors. New application calculations read the integer-cent columns.
- Existing reservation table labels are copied into `restaurant_tables` and linked through `table_id`.
- Existing orphan `ingredient_movements.order_id` values are converted to `NULL` during the foreign-key migration so the historical movement rows can be preserved without violating the new constraint.
- Legacy bundled-meal order lines are copied into `order_items` using archived menu items so historical order totals remain available. The old bundled-meal tables are then dropped. Archived items are hidden from new menu selection.
