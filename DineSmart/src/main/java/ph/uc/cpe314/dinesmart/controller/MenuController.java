package ph.uc.cpe314.dinesmart.controller;

import java.sql.SQLException;
import java.util.List;
import ph.uc.cpe314.dinesmart.data.MenuItemRepository;
import ph.uc.cpe314.dinesmart.model.MenuItem;
import ph.uc.cpe314.dinesmart.service.StaffPermissions;
import ph.uc.cpe314.dinesmart.service.StaffPermissions.Action;

/** Coordinates menu reads and writes without exposing JavaFX controls to the data layer. */
public final class MenuController {
    private final MenuItemRepository repository;

    public MenuController(MenuItemRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    public List<String> loadMenu(String role) throws SQLException {
        require(role, Action.VIEW_MENU);
        return repository.findAvailableSummaries();
    }

    public long create(String role, MenuItem item) throws SQLException {
        return create(role, item, null);
    }

    public long create(String role, MenuItem item, String imagePath)
            throws SQLException {
        return create(role, item, imagePath, null);
    }

    public long create(String role, MenuItem item, String imagePath, String apiMealId)
            throws SQLException {
        require(role, Action.MANAGE_MENU);
        return repository.insert(item, imagePath, apiMealId);
    }

    public boolean delete(String role, String name) throws SQLException {
        require(role, Action.MANAGE_MENU);
        return repository.deleteByName(name);
    }

    public void setDailyAvailability(String role, String name, boolean available)
            throws SQLException {
        require(role, Action.MANAGE_MENU);
        repository.setDailyAvailabilityByName(name, available);
    }

    public void updatePrice(String role, String name, long priceCents)
            throws SQLException {
        require(role, Action.MANAGE_MENU);
        repository.updatePriceByName(name, priceCents);
    }

    public void toggleSoldOut(String role, String name) throws SQLException {
        if (!StaffPermissions.canToggleSoldOut(role)) {
            throw new SecurityException("Your staff role cannot change sold-out status.");
        }
        repository.toggleSoldOutByName(name);
    }

    private static void require(String role, Action action) {
        if (!StaffPermissions.can(role, action)) {
            throw new SecurityException("Your staff role is not allowed to perform "
                    + action.name().toLowerCase().replace('_', ' ') + ".");
        }
    }
}

