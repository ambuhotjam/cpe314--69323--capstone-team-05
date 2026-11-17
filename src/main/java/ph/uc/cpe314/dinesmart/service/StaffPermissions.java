package ph.uc.cpe314.dinesmart.service;

import java.util.Locale;
import java.util.Set;

/**
 * Central role policy used by navigation, controllers, and permission tests.
 * Supervisor and administrator roles can manage all restaurant operations.
 */
public final class StaffPermissions {
    public enum Action {
        VIEW_DASHBOARD,
        VIEW_MENU,
        VIEW_CUSTOMER_ORDERS,
        VIEW_CASHIER,
        VIEW_INVENTORY,
        VIEW_REPORTS,
        VIEW_RESERVATIONS,
        CREATE_ORDER,
        MARK_ORDER_PAID,
        MARK_ORDER_SERVED,
        PREPARE_ORDER,
        COMPLETE_ORDER,
        CANCEL_ORDER,
        MANAGE_STOCK,
        MANAGE_MENU,
        MANAGE_RESERVATIONS,
        MANAGE_STAFF
    }

    private static final String ADMIN = "ADMIN";
    private static final String SUPERVISOR = "SUPERVISOR";
    private static final String WAITER = "WAITER";
    private static final String CASHIER = "CASHIER";
    private static final String KITCHEN = "KITCHEN";

    private StaffPermissions() {
    }

    private static boolean isOneOf(String role, String... allowed) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static boolean management(String role) {
        return isOneOf(role, ADMIN, SUPERVISOR);
    }

    /** Evaluates an explicit action against the signed-in staff role. */
    public static boolean can(String role, Action action) {
        if (action == null) {
            return false;
        }
        if (management(role)) {
            return true;
        }
        return switch (action) {
            case VIEW_MENU, VIEW_CUSTOMER_ORDERS ->
                    isOneOf(role, WAITER, KITCHEN);
            case VIEW_CASHIER, MARK_ORDER_PAID ->
                    isOneOf(role, CASHIER);
            case VIEW_INVENTORY, PREPARE_ORDER, COMPLETE_ORDER ->
                    isOneOf(role, KITCHEN);
            case CREATE_ORDER, MARK_ORDER_SERVED ->
                    isOneOf(role, WAITER);
            case VIEW_DASHBOARD, VIEW_REPORTS, VIEW_RESERVATIONS,
                 CANCEL_ORDER, MANAGE_STOCK, MANAGE_MENU,
                 MANAGE_RESERVATIONS, MANAGE_STAFF -> false;
        };
    }

    public static boolean canViewDashboard(String role) {
        return can(role, Action.VIEW_DASHBOARD);
    }

    public static boolean canViewMenuManagement(String role) {
        return can(role, Action.VIEW_MENU);
    }

    public static boolean canViewCustomerOrders(String role) {
        return can(role, Action.VIEW_CUSTOMER_ORDERS);
    }

    public static boolean canViewCashier(String role) {
        return can(role, Action.VIEW_CASHIER);
    }

    public static boolean canViewBeverages(String role) {
        return management(role);
    }

    public static boolean canViewRecipeDiscovery(String role) {
        return management(role);
    }

    public static boolean canViewReports(String role) {
        return can(role, Action.VIEW_REPORTS);
    }

    public static boolean canSubmitRecommendations(String role) {
        return management(role);
    }

    public static boolean canReviewRecommendations(String role) {
        return management(role);
    }

    public static boolean canViewInventory(String role) {
        return can(role, Action.VIEW_INVENTORY);
    }

    public static boolean canManageIngredientStock(String role) {
        return can(role, Action.MANAGE_STOCK);
    }

    public static boolean canViewSeasonalMenus(String role) {
        return management(role);
    }

    public static boolean canViewReservations(String role) {
        return can(role, Action.VIEW_RESERVATIONS);
    }

    public static boolean canCreateOrders(String role) {
        return can(role, Action.CREATE_ORDER);
    }

    public static boolean canViewActiveOrderQueue(String role) {
        return isOneOf(role, ADMIN, SUPERVISOR, WAITER, KITCHEN);
    }

    public static boolean canViewOrderHistory(String role) {
        return management(role);
    }

    public static boolean canMarkOrdersServed(String role) {
        return can(role, Action.MARK_ORDER_SERVED);
    }

    public static boolean canMarkOrdersPaid(String role) {
        return can(role, Action.MARK_ORDER_PAID);
    }

    public static boolean canPrepareOrders(String role) {
        return can(role, Action.PREPARE_ORDER);
    }

    public static boolean canCompleteOrders(String role) {
        return can(role, Action.COMPLETE_ORDER);
    }

    public static boolean canCancelOrders(String role) {
        return can(role, Action.CANCEL_ORDER);
    }

    public static boolean canResetRestaurantData(String role) {
        return management(role);
    }

    public static boolean canUpdateMenuPrices(String role) {
        return can(role, Action.MANAGE_MENU);
    }

    public static boolean canDeleteMenuItems(String role) {
        return can(role, Action.MANAGE_MENU);
    }

    public static boolean canToggleSoldOut(String role) {
        return management(role) || isOneOf(role, KITCHEN);
    }

    public static boolean canManageDailyMenu(String role) {
        return can(role, Action.MANAGE_MENU);
    }

    public static boolean canCreateMenuItems(String role) {
        return can(role, Action.MANAGE_MENU);
    }

    public static boolean canRefreshMenu(String role) {
        return management(role) || isOneOf(role, CASHIER, KITCHEN);
    }

    public static boolean canManageReservations(String role) {
        return can(role, Action.MANAGE_RESERVATIONS);
    }

    public static boolean canManageStaff(String role) {
        return can(role, Action.MANAGE_STAFF);
    }

    /** The four operational roles used for the restaurant permission matrix. */
    public static Set<String> operationalRoles() {
        return Set.of(SUPERVISOR, WAITER, CASHIER, KITCHEN);
    }

    /** Includes the administrator compatibility role used by seeded accounts. */
    public static Set<String> supportedRoles() {
        return Set.of(ADMIN, SUPERVISOR, WAITER, CASHIER, KITCHEN);
    }
}
