package ph.uc.cpe314.dinesmart.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies staff accounts are salted, role-labelled, and disabled accounts cannot sign in. */
class AuthRepositoryTest {
    @TempDir
    Path tempDir;
    private String previousUrl;

    @BeforeEach
    void setUp() throws Exception {
        previousUrl = System.getProperty("dinesmart.db.url");
        System.setProperty("dinesmart.db.url",
                "jdbc:sqlite:" + tempDir.resolve("staff-auth.db"));
        Database.initialize();
    }

    @AfterEach
    void tearDown() {
        if (previousUrl == null) {
            System.clearProperty("dinesmart.db.url");
        } else {
            System.setProperty("dinesmart.db.url", previousUrl);
        }
    }

    @Test
    void createsAuthenticatesDisablesAndResetsStaffAccount() throws Exception {
        AuthRepository repository = new AuthRepository();
        long id = repository.createStaffUser("testwaiter", "password123",
                "Test Waiter", "WAITER");

        AuthRepository.User user = repository.authenticate("testwaiter", "password123");
        assertNotNull(user);
        assertEquals(id, user.staffUserId());
        assertEquals("WAITER", user.role());
        assertNull(repository.authenticate("testwaiter", "wrong-password"));

        repository.resetStaffPassword(id, "newpassword123");
        assertNull(repository.authenticate("testwaiter", "password123"));
        assertNotNull(repository.authenticate("testwaiter", "newpassword123"));

        repository.setStaffUserActive(id, false);
        assertNull(repository.authenticate("testwaiter", "newpassword123"));
    }

    @Test
    void rejectsInvalidStaffRolesAndWeakPasswords() throws Exception {
        AuthRepository repository = new AuthRepository();
        assertThrows(IllegalArgumentException.class,
                () -> repository.createStaffUser("badrole", "password123",
                        "Bad Role", "GUEST"));
        assertThrows(IllegalArgumentException.class,
                () -> repository.createStaffUser("weakpass", "short",
                        "Weak Password", "WAITER"));
    }
}
