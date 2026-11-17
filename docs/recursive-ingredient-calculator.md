# Recursive Ingredient Calculator

## Purpose

DineSmart uses `RecursiveIngredientCalculator` when a customer places an order. It totals the measurable ingredient requirements for all dishes in that order before the application deducts stock.

This is useful when two or more dishes share an ingredient. The system combines their requirements and checks the combined amount against the available inventory.

## Simple example

A customer orders:

| Dish | Quantity ordered | Chicken per serving | Chicken needed |
|---|---:|---:|---:|
| Chicken Pasta | 2 | 150 g | 300 g |
| Chicken Rice | 1 | 200 g | 200 g |
| **Total** | | | **500 g** |

The calculator returns one total for Chicken: 500 g. DineSmart checks that stock is sufficient, deducts 500 g once, and records one `ORDER_USAGE` movement for that ingredient and order.

## Where recursion happens

File: `src/main/java/ph/uc/cpe314/dinesmart/service/RecursiveIngredientCalculator.java`

Method: `calculateFrom(orderedItems, index, totals)`

- **Base case:** if `index >= orderedItems.size()`, every order line has been processed, so the accumulated totals are returned.
- **Recursive case:** process the current dish, multiply each ingredient's per-serving amount by the ordered quantity, add it to the ingredient total, then call `calculateFrom` again with `index + 1`.
- **Termination:** the index increases by one on every recursive call. It eventually reaches the number of order lines and hits the base case.

## How it connects to the database

`OrderRepository.placeOrder` saves the order and its order lines inside a transaction. It then loads each line's measurable `menu_item_ingredients` records and passes them to `RecursiveIngredientCalculator`. The returned totals are checked against `ingredients.stock_quantity` and deducted. Each deduction is written to `ingredient_movements` with reason `ORDER_USAGE`.

If any ingredient has insufficient stock, the exception causes the transaction to roll back. The order, order lines, stock changes, and movement records are not partially saved.

Recipe scheduling stores automatically deductible numeric measurements in the ingredient's stock unit. Descriptive amounts such as `salt to taste`, `a handful`, or other unparsed measurements are retained in recipe details but are not assigned invented numeric stock deductions.

## How to demonstrate it

1. Create or select two menu items that both use the same inventory ingredient.
2. Set the first dish to use 150 g of Chicken per serving and the second to use 200 g.
3. Set Chicken stock to 1,000 g.
4. Place an order for 2 Chicken Pasta and 1 Chicken Rice.
5. Show that Chicken stock becomes 500 g.
6. Show that `ingredient_movements` contains one `ORDER_USAGE` row for 500 g for that order.
7. Repeat with only 450 g of stock. The order should be rejected and the stock should remain 450 g.

## Defense explanation

"The recursive ingredient calculator processes one ordered dish per call. Its base case is reached when the index reaches the number of order lines. For each dish, it multiplies the ingredient quantity per serving by the number ordered and adds that amount to the running total for that ingredient. It then calls itself for the next dish. After all dishes are processed, the repository checks the combined ingredient totals against stock and deducts each shared ingredient once. If stock is insufficient, the database transaction rolls back."
