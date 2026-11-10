package ph.uc.cpe314.dinesmart.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the same role policy used by the JavaFX navigation and action controls. */
class StaffPermissionsTest {
    @Test void administratorCanAccessAllOperationalAreasAndManagementActions() {
        String role = "ADMIN";
        assertAll(
                () -> assertTrue(StaffPermissions.canViewDashboard(role)),
                () -> assertTrue(StaffPermissions.canViewMenuManagement(role)),
                () -> assertTrue(StaffPermissions.canViewCustomerOrders(role)),
                () -> assertTrue(StaffPermissions.canViewCashier(role)),
                () -> assertTrue(StaffPermissions.canViewBeverages(role)),
                () -> assertTrue(StaffPermissions.canViewRecipeDiscovery(role)),
                () -> assertTrue(StaffPermissions.canViewReports(role)),
                () -> assertTrue(StaffPermissions.canViewInventory(role)),
                () -> assertTrue(StaffPermissions.canViewSeasonalMenus(role)),
                () -> assertTrue(StaffPermissions.canViewReservations(role)),
                () -> assertTrue(StaffPermissions.canManageIngredientStock(role)),
                () -> assertTrue(StaffPermissions.canMarkOrdersPaid(role)),
                () -> assertTrue(StaffPermissions.canMarkOrdersServed(role)),
                () -> assertTrue(StaffPermissions.canPrepareOrders(role)),
                () -> assertTrue(StaffPermissions.canCompleteOrders(role)),
                () -> assertTrue(StaffPermissions.canCancelOrders(role)),
                () -> assertTrue(StaffPermissions.canCreateMenuItems(role))
        );
    }

    @Test void supervisorCanManageRestaurantOperations() {
        String role = "SUPERVISOR";
        assertTrue(StaffPermissions.canViewDashboard(role));
        assertTrue(StaffPermissions.canManageIngredientStock(role));
        assertTrue(StaffPermissions.canManageDailyMenu(role));
        assertTrue(StaffPermissions.canUpdateMenuPrices(role));
        assertTrue(StaffPermissions.canDeleteMenuItems(role));
        assertTrue(StaffPermissions.canMarkOrdersPaid(role));
        assertTrue(StaffPermissions.canMarkOrdersServed(role));
        assertTrue(StaffPermissions.canReviewRecommendations(role));
        assertTrue(StaffPermissions.canSubmitRecommendations(role));
    }

    @Test void waiterCanCreateOrdersAndMarkCompletedOrdersServedButCannotManageStock() {
        String role = "WAITER";
        assertTrue(StaffPermissions.canViewMenuManagement(role));
        assertTrue(StaffPermissions.canViewCustomerOrders(role));
        assertTrue(StaffPermissions.canCreateOrders(role));
        assertTrue(StaffPermissions.canMarkOrdersServed(role));
        assertFalse(StaffPermissions.canViewDashboard(role));
        assertFalse(StaffPermissions.canViewCashier(role));
        assertFalse(StaffPermissions.canViewInventory(role));
        assertFalse(StaffPermissions.canManageIngredientStock(role));
        assertFalse(StaffPermissions.canMarkOrdersPaid(role));
        assertFalse(StaffPermissions.canPrepareOrders(role));
        assertFalse(StaffPermissions.canCreateMenuItems(role));
        assertFalse(StaffPermissions.canRefreshMenu(role));
    }

    @Test void cashierCanReviewBillsAndMarkOrdersPaidOnly() {
        String role = "CASHIER";
        assertTrue(StaffPermissions.canViewCashier(role));
        assertTrue(StaffPermissions.canMarkOrdersPaid(role));
        assertFalse(StaffPermissions.canViewCustomerOrders(role));
        assertFalse(StaffPermissions.canCreateOrders(role));
        assertFalse(StaffPermissions.canMarkOrdersServed(role));
        assertFalse(StaffPermissions.canPrepareOrders(role));
        assertFalse(StaffPermissions.canViewInventory(role));
        assertFalse(StaffPermissions.canViewReports(role));
    }

    @Test void kitchenCanPrepareOrdersAndInspectInventoryWithoutCashierOrWaiterActions() {
        String role = "KITCHEN";
        assertTrue(StaffPermissions.canViewMenuManagement(role));
        assertTrue(StaffPermissions.canViewCustomerOrders(role));
        assertTrue(StaffPermissions.canViewInventory(role));
        assertTrue(StaffPermissions.canPrepareOrders(role));
        assertTrue(StaffPermissions.canCompleteOrders(role));
        assertTrue(StaffPermissions.canToggleSoldOut(role));
        assertFalse(StaffPermissions.canManageIngredientStock(role));
        assertFalse(StaffPermissions.canMarkOrdersPaid(role));
        assertFalse(StaffPermissions.canMarkOrdersServed(role));
        assertFalse(StaffPermissions.canCreateMenuItems(role));
    }

    @Test void roleNamesAreCaseInsensitiveAndUnknownRolesAreDeniedByDefault() {
        assertTrue(StaffPermissions.canViewCashier(" cashier "));
        assertFalse(StaffPermissions.canViewDashboard("GUEST"));
        assertFalse(StaffPermissions.canViewCustomerOrders("GUEST"));
        assertFalse(StaffPermissions.canViewActiveOrderQueue("GUEST"));
        assertFalse(StaffPermissions.canRefreshMenu("GUEST"));
        assertFalse(StaffPermissions.canMarkOrdersPaid("GUEST"));
        assertFalse(StaffPermissions.canViewInventory(null));
        assertFalse(StaffPermissions.canCreateOrders("  "));
        assertEquals(5, StaffPermissions.supportedRoles().size());
    }
}
