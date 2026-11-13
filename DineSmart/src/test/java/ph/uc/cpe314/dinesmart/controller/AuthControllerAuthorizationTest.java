package ph.uc.cpe314.dinesmart.controller;

import org.junit.jupiter.api.Test;
import ph.uc.cpe314.dinesmart.data.AuthRepository;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerAuthorizationTest {
    private final AuthController controller =
            new AuthController(new AuthRepository());

    @Test
    void operationalRolesCannotManageStaffAccounts() {
        for (String role : new String[] {"WAITER", "CASHIER", "KITCHEN"}) {
            assertThrows(SecurityException.class,
                    () -> controller.staffUsers(role));
            assertThrows(SecurityException.class,
                    () -> controller.createStaffUser(role, "newuser",
                            "password123", "New User", "WAITER"));
            assertThrows(SecurityException.class,
                    () -> controller.setStaffUserActive(role, 1L, false));
            assertThrows(SecurityException.class,
                    () -> controller.resetStaffPassword(role, 1L, "password123"));
        }
    }
}
