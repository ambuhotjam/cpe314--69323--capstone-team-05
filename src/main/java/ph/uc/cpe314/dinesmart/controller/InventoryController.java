package ph.uc.cpe314.dinesmart.controller;

import java.sql.SQLException;
import java.util.List;
import ph.uc.cpe314.dinesmart.data.IngredientRepository;
import ph.uc.cpe314.dinesmart.service.StaffPermissions;

/** Controls inventory access and stock adjustments for authorized staff. */
public final class InventoryController {
    private final IngredientRepository repository;

    public InventoryController(IngredientRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    public List<IngredientRepository.Ingredient> loadStock(String role)
            throws SQLException {
        requireView(role);
        return repository.findAll();
    }

    public void adjust(String role, String ingredient, double quantity, boolean add)
            throws SQLException {
        requireManage(role);
        repository.adjustStock(ingredient, quantity, add);
    }

    public void save(String role, String name, double quantity, String unit)
            throws SQLException {
        requireManage(role);
        repository.save(name, quantity, unit);
    }

    public void delete(String role, String ingredientName) throws SQLException {
        requireManage(role);
        repository.delete(ingredientName);
    }

    private static void requireView(String role) {
        if (!StaffPermissions.canViewInventory(role)) {
            throw new SecurityException("Your staff role cannot view inventory.");
        }
    }

    private static void requireManage(String role) {
        if (!StaffPermissions.canManageIngredientStock(role)) {
            throw new SecurityException("Only management staff can change ingredient stock.");
        }
    }
}
