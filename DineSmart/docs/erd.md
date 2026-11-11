# DineSmart Entity-Relationship Diagram

**CPE 314 · Section 3B · Team 05 · Milestone 2 (Week 10)**

The schema contains the 15 original application tables plus `restaurant_tables`, added to normalize table assignments. The current ERD therefore contains **16 tables**.

![Figure 3. Finalized DineSmart ERD](erd.png)

## Mermaid ERD

```mermaid
erDiagram
    RESTAURANT_TABLES o|--o{ RESTAURANT_ORDERS : assigned_to
    STAFF_USERS o|--o{ RESTAURANT_ORDERS : creates
    RESTAURANT_TABLES o|--o{ TABLE_RESERVATIONS : reserved_for

    RESTAURANT_ORDERS ||--o{ ORDER_ITEMS : contains
    MENU_ITEMS ||--o{ ORDER_ITEMS : ordered_as
    RESTAURANT_ORDERS ||--o{ ORDER_COMBO_ITEMS : contains
    COMBOS ||--o{ ORDER_COMBO_ITEMS : ordered_as

    MENU_ITEMS ||--o{ MENU_ITEM_INGREDIENTS : requires
    INGREDIENTS ||--o{ MENU_ITEM_INGREDIENTS : used_by
    MENU_ITEMS ||--o{ MENU_PERIODS : scheduled_in

    RECIPE_CATALOG ||--o{ RECIPE_INGREDIENTS : describes
    INGREDIENTS ||--o{ INGREDIENT_MOVEMENTS : has_movements
    RESTAURANT_ORDERS o|--o{ INGREDIENT_MOVEMENTS : causes

    COMBOS ||--o{ COMBO_COMPONENTS : contains
    MENU_ITEMS o|--o{ COMBO_COMPONENTS : menu_component
    COMBOS o|--o{ COMBO_COMPONENTS : nested_combo

    RESTAURANT_TABLES {
        INTEGER table_id PK
        TEXT table_label UK
        INTEGER capacity
        INTEGER active
    }
    STAFF_USERS {
        INTEGER staff_user_id PK
        TEXT username UK
        TEXT password_hash
        TEXT password_salt
        TEXT display_name
        TEXT role
        INTEGER active
    }
    MENU_ITEMS {
        INTEGER menu_item_id PK
        TEXT name UK
        TEXT item_type
        INTEGER price_cents
        REAL price
        INTEGER base_prep_minutes
        TEXT api_meal_id
        TEXT image_path
        INTEGER is_sold_out
        INTEGER available_today
        TEXT daily_menu_date
        INTEGER is_archived
    }
    RESTAURANT_ORDERS {
        TEXT order_id PK
        TEXT ordered_at
        TEXT status
        TEXT payment_status
        TEXT paid_at
        TEXT service_status
        TEXT served_at
        TEXT customer_name
        INTEGER table_id FK
        INTEGER created_by FK
    }
    ORDER_ITEMS {
        INTEGER order_item_id PK
        TEXT order_id FK
        INTEGER menu_item_id FK
        INTEGER quantity
        INTEGER unit_price_cents
        REAL unit_price_at_order
    }
    ORDER_COMBO_ITEMS {
        INTEGER order_combo_item_id PK
        TEXT order_id FK
        INTEGER combo_id FK
        INTEGER quantity
        INTEGER unit_price_cents
        REAL unit_price_at_order
    }
    INGREDIENTS {
        INTEGER ingredient_id PK
        TEXT name UK
        REAL stock_quantity
        TEXT stock_unit
    }
    MENU_ITEM_INGREDIENTS {
        INTEGER menu_item_id PK, FK
        INTEGER ingredient_id PK, FK
        REAL quantity_required
        TEXT measure
    }
    MENU_PERIODS {
        INTEGER period_id PK
        INTEGER menu_item_id FK
        TEXT period_name
        TEXT starts_on
        TEXT ends_on
        INTEGER active
    }
    RECIPE_CATALOG {
        INTEGER recipe_id PK
        TEXT source
        TEXT external_id
        TEXT name UK
        TEXT category
        TEXT area
        TEXT instructions
        TEXT thumbnail_url
        TEXT ingredients_text
        TEXT created_at
    }
    RECIPE_INGREDIENTS {
        INTEGER recipe_id PK, FK
        TEXT ingredient_name PK
        TEXT source_measure
        REAL quantity
        TEXT unit
        TEXT parse_status
    }
    TABLE_RESERVATIONS {
        INTEGER reservation_id PK
        TEXT guest_name
        TEXT guest_phone
        INTEGER party_size
        TEXT reservation_at
        TEXT table_label
        INTEGER table_id FK
        TEXT notes
        TEXT status
        TEXT created_at
    }
    MENU_RECOMMENDATIONS {
        INTEGER recommendation_id PK
        TEXT suggested_item
        TEXT reason
        TEXT suggested_by
        TEXT status
        TEXT created_at
    }
    INGREDIENT_MOVEMENTS {
        INTEGER movement_id PK
        INTEGER ingredient_id FK
        TEXT order_id FK
        REAL quantity_change
        TEXT reason
        TEXT created_at
    }
    COMBOS {
        INTEGER combo_id PK
        TEXT name UK
        INTEGER discount_basis_points
        REAL discount_percent
    }
    COMBO_COMPONENTS {
        INTEGER component_id PK
        INTEGER combo_id FK
        INTEGER menu_item_id FK
        INTEGER child_combo_id FK
        INTEGER quantity
    }
```

## Relationship cardinalities

| Parent | Child | Cardinality | Meaning |
|---|---|---:|---|
| `restaurant_tables` | `restaurant_orders` | 1 to 0..many | An order may be unassigned to a table; a table can be linked to many orders over time. |
| `staff_users` | `restaurant_orders` | 1 to 0..many | `created_by` is optional for imported or historical orders. |
| `restaurant_tables` | `table_reservations` | 1 to 0..many | A reservation may be unassigned; assigned reservations reference one table. |
| `restaurant_orders` | `order_items` | 1 to many | Each order item belongs to one order. |
| `menu_items` | `order_items` | 1 to many | Each order item references one menu item. |
| `restaurant_orders` | `order_combo_items` | 1 to many | Each combo order line belongs to one order. |
| `combos` | `order_combo_items` | 1 to many | Each combo order line references one combo. |
| `menu_items` | `menu_item_ingredients` | 1 to many | One menu item can require multiple ingredients. |
| `ingredients` | `menu_item_ingredients` | 1 to many | One ingredient can be used by multiple menu items. |
| `menu_items` | `menu_periods` | 1 to many | A menu item can have multiple meal-period schedules. |
| `recipe_catalog` | `recipe_ingredients` | 1 to many | A recipe can contain multiple ingredient descriptions. |
| `ingredients` | `ingredient_movements` | 1 to many | An ingredient has an inventory movement history. |
| `restaurant_orders` | `ingredient_movements` | 1 to many, optional | Order-related movements point to an order; manual movements may have a null order ID. |
| `combos` | `combo_components` | 1 to many | A combo can contain menu items or nested combos. |
| `menu_items` | `combo_components` | 1 to many, optional | A component may reference a menu item. |
| `combos` | `combo_components` | 1 to many, optional self-reference | A component may reference a child combo. |

## Schema notes

- `menu_items.price_cents` is the canonical selling price. The old `price` column is retained and updated as a compatibility mirror for older database files.
- `order_items.unit_price_cents` and `order_combo_items.unit_price_cents` preserve the exact order-time price. The old `unit_price_at_order` columns remain compatibility mirrors.
- `combos.discount_basis_points` is the canonical integer discount percentage representation (100 basis points = 1%). `discount_percent` is retained for older databases.
- `table_reservations.table_label` remains as a readable legacy field; `table_id` is the normalized foreign-key relationship.
- `ingredient_movements.order_id` now references `restaurant_orders.order_id` with `ON DELETE SET NULL`.
- `recipe_ingredients.ingredient_name` and `menu_recommendations.suggested_by` are descriptive text fields, not foreign keys in the current schema.
