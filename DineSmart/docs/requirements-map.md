# Requirement-to-Code Map

**CPE 314 · Section 3B · Team 05 · Milestone 2 (Week 10)**

| Requirement / user action | Main code | Supporting method or test |
|---|---|---|
| Staff login with salted password hash | `data/AuthRepository.java` | `authenticate`, `hash`, `AuthRepositoryTest` |
| Create, disable, and reset staff accounts | `data/AuthRepository.java` | `createStaffUser`, `setStaffUserActive`, `resetStaffPassword` |
| Central role and action policy | `service/StaffPermissions.java` | `can`, role helper methods, `StaffPermissionsTest` |
| Reject an unauthorized order action | `controller/OrderController.java` | `placeOrder`, `markPaid`, `markServed`, `changeStatus`, `OrderControllerAuthorizationTest` |
| Create orders with optional table and staff owner | `data/OrderRepository.java` | `placeOrder(customerName, items, tableLabel, staffUserId)` |
| Filter the signed-in staff member's daily orders | `data/OrderReportRepository.java` | `findOrdersCreatedByToday` |
| Daily sales grouped by staff | `data/OrderReportRepository.java`, `controller/ReportController.java` | `getTodaySalesByStaffCents`, `todaySalesByStaffCents` |
| Exact menu prices | `util/Money.java`, `model/MenuItem.java`, `data/MenuItemRepository.java` | `parseCents`, `formatCents`, `price_cents` |
| Exact order-time totals | `data/OrderReportRepository.java` | `unit_price_cents`, `getTodaySalesCents`, `findCashierTotals` |
| Link reservations to tables | `data/ReservationRepository.java`, `data/Database.java` | `resolveTableId`, `table_reservations.table_id` |
| Link orders to tables | `data/OrderRepository.java`, `data/Database.java` | `restaurant_orders.table_id` |
| Link inventory movements to orders | `data/Database.java` | `migrateIngredientMovementOrderForeignKey` |
| Restore stock after cancellation | `data/OrderRepository.java` | `restoreOrderIngredients`, `cancelPendingOrder`, `changeStatus` |
| Audit stock deductions and restoration | `data/OrderRepository.java` | `ORDER_USAGE`, `ORDER_CANCEL_RESTORE` |
| TheMealDB search and category browse | `service/TheMealDbService.java` | `searchByName`, `searchByCategory` |
| TheMealDB recipe details | `dto/MealDbMealDto.java`, `service/MealDbMealMapper.java` | `fromJson`, `toDomain` |
| Typed API outcomes | `service/ApiResult.java`, `service/TheMealDbService.java` | `OK`, `EMPTY`, `FAILED`, `searchByNameResult` |
| TheMealDB API failure paths | `src/test/java/ph/uc/cpe314/dinesmart/service/TheMealDbServiceTest.java` | success, empty, 503, timeout, malformed JSON |
| Menu management | `controller/MenuController.java`, `data/MenuItemRepository.java` | `create`, `updatePrice`, `delete`, `toggleSoldOut` |
| Inventory access and stock updates | `controller/InventoryController.java`, `data/IngredientRepository.java` | `loadStock`, `adjust`, `save`, `delete` |
| Reservation management | `controller/ReservationController.java`, `data/ReservationRepository.java` | `list`, `book`, `updateStatus` |
| Sales and order analytics | `controller/ReportController.java`, `data/OrderReportRepository.java` | `bestSellers`, `ordersByHour`, `todaySalesCents` |
| Background I/O and responsive UI | `ui/DineSmartApp.java` | named `ioExecutor`, `CompletableFuture`, `Platform.runLater`, `stop` |
| Database schema and repeatable migrations | `data/Database.java` | `initialize`, `migrateLegacyColumns`, `backfillRestaurantTableLinks` |
| ERD and cardinalities | `docs/erd.md`, `docs/erd.png` | all 16 tables and foreign-key relationships |

## Verification commands

```bash
mvn clean test
mvn javafx:run
```
