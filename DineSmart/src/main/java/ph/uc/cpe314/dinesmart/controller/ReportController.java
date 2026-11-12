package ph.uc.cpe314.dinesmart.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import ph.uc.cpe314.dinesmart.data.OrderReportRepository;
import ph.uc.cpe314.dinesmart.data.MenuItemRepository;
import ph.uc.cpe314.dinesmart.service.StaffPermissions;

/** Supplies analytics data to report views; aggregation remains in repositories. */
public final class ReportController {
    private final OrderReportRepository orders;
    private final MenuItemRepository menu;

    public ReportController(OrderReportRepository orders, MenuItemRepository menu) {
        this.orders = java.util.Objects.requireNonNull(orders);
        this.menu = java.util.Objects.requireNonNull(menu);
    }

    public long todaySalesCents(String role) throws SQLException {
        requireManagement(role);
        return orders.getTodaySalesCents();
    }

    public List<OrderReportRepository.BestSeller> bestSellers(String role, int limit)
            throws SQLException {
        requireManagement(role);
        return orders.findBestSellers(limit);
    }

    public Map<String, Long> todaySalesByStaffCents(String role)
            throws SQLException {
        requireManagement(role);
        return orders.getTodaySalesByStaffCents();
    }

    public Map<Integer, Integer> ordersByHour(String role) throws SQLException {
        requireManagement(role);
        return orders.getOrdersByHour();
    }

    public Map<String, Integer> menuCategoryFrequency(String role)
            throws SQLException {
        requireManagement(role);
        return menu.countByItemType();
    }

    private static void requireManagement(String role) {
        if (!StaffPermissions.canViewReports(role)) {
            throw new SecurityException("Your staff role cannot view reports.");
        }
    }
}

