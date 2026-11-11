package ph.uc.cpe314.dinesmart.service;
import java.util.Locale;
import java.util.Set;

/**
 * Central role policy for DineSmart navigation and staff actions.
 * Unknown or null roles are denied by default.
 */
public final class StaffPermissions {
    private static final String ADMIN = "ADMIN";
    private static final String SUPERVISOR = "SUPERVISOR";
    private static final String WAITER = "WAITER";
    private static final String CASHIER = "CASHIER";
    private static final String KITCHEN = "KITCHEN";

    private StaffPermissions() { }

    private static boolean isOneOf(String role, String... allowed) {
        if (role == null || role.isBlank()) return false;
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) return true;
        }
        return false;
    }

    private static boolean management(String role) {
        return isOneOf(role, ADMIN, SUPERVISOR);
    }

    public static boolean canViewDashboard(String role) { return management(role); }
    public static boolean canViewMenuManagement(String role) { return isOneOf(role, ADMIN, SUPERVISOR, WAITER, KITCHEN); }
    public static boolean canViewCustomerOrders(String role) { return isOneOf(role, ADMIN, SUPERVISOR, WAITER, KITCHEN); }
    public static boolean canViewCashier(String role) { return isOneOf(role, ADMIN, SUPERVISOR, CASHIER); }
    public static boolean canViewBeverages(String role) { return management(role); }
    public static boolean canViewRecipeDiscovery(String role) { return management(role); }
    public static boolean canViewReports(String role) { return management(role); }
    public static boolean canSubmitRecommendations(String role) { return isOneOf(role, SUPERVISOR); }
    public static boolean canReviewRecommendations(String role) { return management(role); }
    public static boolean canViewInventory(String role) { return isOneOf(role, ADMIN, SUPERVISOR, KITCHEN); }
    public static boolean canManageIngredientStock(String role) { return management(role); }
    public static boolean canViewSeasonalMenus(String role) { return management(role); }
    public static boolean canViewReservations(String role) { return management(role); }
    public static boolean canCreateOrders(String role) { return isOneOf(role, ADMIN, SUPERVISOR, WAITER); }
    public static boolean canViewActiveOrderQueue(String role) { return isOneOf(role, ADMIN, SUPERVISOR, WAITER, KITCHEN); }
    public static boolean canViewOrderHistory(String role) { return management(role); }
    public static boolean canMarkOrdersServed(String role) { return isOneOf(role, ADMIN, SUPERVISOR, WAITER); }
    public static boolean canMarkOrdersPaid(String role) { return isOneOf(role, ADMIN, SUPERVISOR, CASHIER); }
    public static boolean canPrepareOrders(String role) { return isOneOf(role, ADMIN, SUPERVISOR, KITCHEN); }
    public static boolean canCompleteOrders(String role) { return isOneOf(role, ADMIN, SUPERVISOR, KITCHEN); }
    public static boolean canCancelOrders(String role) { return management(role); }
    public static boolean canResetRestaurantData(String role) { return management(role); }
    public static boolean canUpdateMenuPrices(String role) { return management(role); }
    public static boolean canDeleteMenuItems(String role) { return management(role); }
    public static boolean canToggleSoldOut(String role) { return isOneOf(role, ADMIN, SUPERVISOR, KITCHEN); }
    public static boolean canManageDailyMenu(String role) { return management(role); }
    public static boolean canCreateMenuItems(String role) { return management(role); }
    public static boolean canRefreshMenu(String role) { return isOneOf(role, ADMIN, SUPERVISOR, CASHIER, KITCHEN); }

    /** All supported role names, useful for validation and role-matrix tests. */
    public static Set<String> supportedRoles() {
        return Set.of(ADMIN, SUPERVISOR, WAITER, CASHIER, KITCHEN);
    }
}
