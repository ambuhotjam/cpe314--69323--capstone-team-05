## ONE-PARAGRAPH PITCH

Our application, Restaurant Ordering \& Inventory Insights, is for staff of small restaurants and campus canteens. It will manage menu items, customer orders, the kitchen queue and ingredient stock. Staff can import real recipes, create nested meal combos and cancel the latest eligible pending order. Sales charts will show best-selling dishes and busy ordering hours to help staff plan stock and preparation.



## ENTITY-RELATIONSHIP SKETCH

## Table 1: Entities and Their Key Attributes

| Entity | Key attributes and other fields |
|---|---|
| **MenuItem** | **Keys:** `menu_item_id` PK  <br> **Other fields:** `name`, `item_type`, `price`, `base_prep_minutes`, `api_meal_id` |
| **RestaurantOrder** | **Keys:** `order_id` PK  <br> **Other fields:** `ordered_at`, `status` |
| **OrderItem** | **Keys:** `order_item_id` PK; `order_id` FK; `menu_item_id` FK  <br> **Other fields:** `quantity`, `unit_price_at_order` |
| **Ingredient** | **Keys:** `ingredient_id` PK  <br> **Other fields:** `name`, `stock_quantity`, `stock_unit` |
| **MenuItemIngredient** | **Keys:** `menu_item_id` PK/FK; `ingredient_id` PK/FK  <br> **Other fields:** `quantity_required` |
| **Combo** | **Keys:** `combo_id` PK  <br> **Other fields:** `name` |
| **ComboComponent** | **Keys:** `component_id` PK; `combo_id` FK; `menu_item_id` FK (nullable); `child_combo_id` FK (nullable)  <br> **Other fields:** `quantity` |



## Table 2: Relationships and what they mean

| Relationship | Type | Meaning |
| :--- | :---: | :--- |
| RestaurantOrder to OrderItem | 1:M| One order contains several ordered-item records. |
| MenuItem to OrderItem | 1:M | One menu item can appear in several orders. |
| MenuItem to MenuItemIngredient | 1:M| One menu item can require several ingredients. |
| Ingredient to MenuItemIngredient | 1:M | One ingredient can be used by several menu items. |
| Combo to ComboComponent | 1:M | One combo contains several component records |
| MenuItem to ComboComponent | 1:M | One menu item can be used in several combo components |
| Combo to ComboComponent (as child) | 1:M | One child combo can be used in several parent-combo components |

1:M means one-to-many. OrderItem links orders with menu items; MenuItemIngredient links menu items with ingredients.

Combo rule: each component points to exactly one menu item OR one child combo. Quantities must be positive. A combo must not contain itself, directly or indirectly.
