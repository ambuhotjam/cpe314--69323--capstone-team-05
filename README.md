## ONE-PARAGRAPH PITCH

Our application, Restaurant Ordering \& Inventory Insights, is for staff of small restaurants and campus canteens. It will manage menu items, customer orders, the kitchen queue and ingredient stock. Staff can import real recipes, create nested meal combos and cancel the latest eligible pending order. Sales charts will show best-selling dishes and busy ordering hours to help staff plan stock and preparation.



\## Entity-Relationship Sketch



\## Table 1: Entities and Their Key Attributes



\### MenuItem

\*\*Keys\*\*

\- `menu\_item\_id` (PK)



\*\*Other fields\*\*

\- `name`

\- `item\_type`

\- `price`

\- `base\_prep\_minutes`

\- `api\_meal\_id`



\---



\### RestaurantOrder

\*\*Keys\*\*

\- `order\_id` (PK)



\*\*Other fields\*\*

\- `ordered\_at`

\- `status`



\---



\### OrderItem

\*\*Keys\*\*

\- `order\_item\_id` (PK)

\- `order\_id` (FK)

\- `menu\_item\_id` (FK)



\*\*Other fields\*\*

\- `quantity`

\- `unit\_price\_at\_order`



\---



\### Ingredient

\*\*Keys\*\*

\- `ingredient\_id` (PK)



\*\*Other fields\*\*

\- `name`

\- `stock\_quantity`

\- `stock\_unit`



\---



\### MenuItemIngredient

\*\*Keys\*\*

\- `menu\_item\_id` (PK, FK)

\- `ingredient\_id` (PK, FK)



\*\*Other fields\*\*

\- `quantity\_required`



\---



\### Combo

\*\*Keys\*\*

\- `combo\_id` (PK)



\*\*Other fields\*\*

\- `name`



\---



\### ComboComponent

\*\*Keys\*\*

\- `component\_id` (PK)

\- `combo\_id` (FK)

\- `menu\_item\_id` (FK, nullable)

\- `child\_combo\_id` (FK, nullable)



\*\*Other fields\*\*

\- `quantity`



\---



\## Notes



\- \*\*PK (Primary Key):\*\* Identifies a unique record in a table.

\- \*\*FK (Foreign Key):\*\* References a record in another table.

\- \*\*PK/FK:\*\* A field that serves as both a primary key and a foreign key.

\- The two PK/FK fields in `MenuItemIngredient` together form a \*\*composite primary key\*\*.

