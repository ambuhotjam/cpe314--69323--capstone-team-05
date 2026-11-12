# DineSmart — Restaurant Ordering & Inventory Insights

**CPE 314 · Section 3B · Team 05 · Milestone 2 (Week 10)**

DineSmart is a JavaFX restaurant operations application with menu management, staff login and role permissions, customer orders, kitchen workflow, cashier payment tracking, reservations, ingredient inventory, recipe discovery through TheMealDB, and operational reports.

## Requirements

- JDK 17 or later
- Maven 3.9+
- Internet connection for live TheMealDB searches and image downloads
- The recipe browser maps DineSmart Main Course to Beef, Chicken, Goat, Lamb, Miscellaneous, Pasta, Pork, Seafood, Vegan, and Vegetarian; Appetizer maps to TheMealDB Starter.
- SQLite database is created locally as `dinesmart.db`

## Build and test

From the repository root:

```bash
mvn clean test
```

Run the JavaFX application:

```bash
mvn javafx:run
```

The Maven test suite uses temporary SQLite databases. The TheMealDB service tests use a local HTTP server and do not require the public API.

## Demo staff accounts

| Role | Username | Password |
|---|---|---|
| Administrator | `admin` | `admin123` |
| Supervisor | `supervisor` | `super123` |
| Waiter | `waiter` | `waiter123` |
| Cashier | `cashier` | `cashier123` |
| Kitchen | `kitchen` | `kitchen123` |

Change demo passwords before using the application with real restaurant data. Passwords are stored as salted PBKDF2 hashes, not as plain text.

## Project structure

```text
src/main/java/ph/uc/cpe314/dinesmart/
├── controller/   # Role-aware application controllers
├── data/         # SQLite repositories and schema migrations
├── dto/          # External API response DTOs
├── model/        # Domain models
├── service/      # API, permissions, queues, and business utilities
├── ui/           # JavaFX application and views
└── util/         # Shared utilities, including exact-cent money handling
```

## Important implementation notes

- Monetary values are calculated and persisted using integer cents (`price_cents` and `unit_price_cents`). Legacy decimal columns remain as compatibility mirrors for existing database files.
- Ingredient quantities are physical measurements and remain decimal quantities.
- Orders can optionally link to a row in `restaurant_tables` and to the staff account that created them.
- Bundled-meal ordering has been removed. On startup, legacy bundled-meal order lines are preserved as archived menu-item order lines before the old schema tables are dropped.
- JavaFX `ComboBox` controls remain as ordinary dropdown selectors; they are not bundled-meal ordering features.
- Order cancellation restores ingredient stock in the same database transaction and writes an `ORDER_CANCEL_RESTORE` inventory movement.
- `RecursiveIngredientCalculator` uses a base case and recursive calls to total measurable ingredient requirements across all dishes in an order. Shared ingredients are deducted once per order; descriptive measurements such as `to taste` are not assigned guessed stock quantities.
- `ingredient_movements.order_id` is a foreign key to `restaurant_orders.order_id`.
- `docs/design-document.md` contains the Week 10 design document. `docs/erd.png` and `docs/erd.md` contain the 13-table ERD and relationship details. `docs/recursive-ingredient-calculator.md` explains the recursion, test scenario, and defense walkthrough.
- `KNOWN_ISSUES.md` records the remaining work honestly; see it before the demonstration.
