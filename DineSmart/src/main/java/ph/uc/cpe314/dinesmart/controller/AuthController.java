package ph.uc.cpe314.dinesmart.controller;

import java.sql.SQLException;
import java.util.List;
import ph.uc.cpe314.dinesmart.service.StaffPermissions;
import ph.uc.cpe314.dinesmart.data.AuthRepository;

/** Owns staff login state and delegates credential verification to the repository. */
public final class AuthController {
    private final AuthRepository repository;
    private AuthRepository.User currentUser;

    public AuthController(AuthRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    public AuthRepository.User login(String username, String password)
            throws SQLException {
        currentUser = repository.authenticate(username, password);
        return currentUser;
    }

    public void logout() {
        currentUser = null;
    }

    public List<AuthRepository.User> staffUsers(String role) throws SQLException {
        requireStaffManagement(role);
        return repository.findStaffUsers();
    }

    public long createStaffUser(String role, String username, String password,
                                String displayName, String staffRole)
            throws SQLException {
        requireStaffManagement(role);
        return repository.createStaffUser(username, password, displayName, staffRole);
    }

    public void setStaffUserActive(String role, long staffUserId, boolean active)
            throws SQLException {
        requireStaffManagement(role);
        repository.setStaffUserActive(staffUserId, active);
    }

    public void resetStaffPassword(String role, long staffUserId, String password)
            throws SQLException {
        requireStaffManagement(role);
        repository.resetStaffPassword(staffUserId, password);
    }

    private static void requireStaffManagement(String role) {
        if (!StaffPermissions.canManageStaff(role)) {
            throw new SecurityException("Your staff role cannot manage staff accounts.");
        }
    }

    public AuthRepository.User currentUser() {
        return currentUser;
    }
}
