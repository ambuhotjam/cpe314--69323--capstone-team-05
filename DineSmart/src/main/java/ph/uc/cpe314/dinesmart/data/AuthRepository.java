package ph.uc.cpe314.dinesmart.data;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;

/** Local account authentication for the desktop prototype. */
public final class AuthRepository {
    public record User(long staffUserId, String username, String displayName,
                       String role, boolean active) {
        @Override
        public String toString() {
            return displayName + " (" + username + ") — " + role
                    + (active ? " · ACTIVE" : " · DISABLED");
        }
    }
    private static final SecureRandom RANDOM = new SecureRandom();

    public void seedDemoAccounts() throws SQLException {
        String[][] accounts = {
            {"admin", "admin123", "Restaurant Administrator", "ADMIN"},
            {"supervisor", "super123", "Restaurant Supervisor", "SUPERVISOR"},
            {"waiter", "waiter123", "Service Staff", "WAITER"},
            {"cashier", "cashier123", "Front Desk Cashier", "CASHIER"},
            {"kitchen", "kitchen123", "Kitchen Staff", "KITCHEN"}
        };
        String sql = "INSERT OR IGNORE INTO staff_users(username,password_hash,password_salt,display_name,role,active) VALUES(?,?,?,?,?,1)";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (String[] a : accounts) {
                byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
                ps.setString(1, a[0]); ps.setString(2, hash(a[1], salt));
                ps.setString(3, java.util.Base64.getEncoder().encodeToString(salt));
                ps.setString(4, a[2]); ps.setString(5, a[3]); ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public User authenticate(String username, String password) throws SQLException {
        if (username == null || password == null) { return null; }
        String sql = "SELECT staff_user_id,username,display_name,role,password_hash,password_salt,active FROM staff_users WHERE username=? AND active=1";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { return null; }
                byte[] salt = java.util.Base64.getDecoder().decode(rs.getString("password_salt"));
                byte[] expected = java.util.HexFormat.of().parseHex(rs.getString("password_hash"));
                byte[] actual = java.util.HexFormat.of().parseHex(hash(password, salt));
                if (!MessageDigest.isEqual(expected, actual)) { return null; }
                return new User(rs.getLong("staff_user_id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getString("role"),
                        rs.getInt("active") == 1);
            }
        }
    }

    /** Lists staff accounts without exposing password hashes or salts. */
    public List<User> findStaffUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT staff_user_id, username, display_name, role, active
                     FROM staff_users
                     ORDER BY role, display_name
                     """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new User(rs.getLong("staff_user_id"),
                        rs.getString("username"), rs.getString("display_name"),
                        rs.getString("role"), rs.getInt("active") == 1));
            }
        }
        return users;
    }

    /** Creates a staff account using a salted PBKDF2 password hash. */
    public long createStaffUser(String username, String password,
                                String displayName, String role) throws SQLException {
        if (username == null || !username.matches("[A-Za-z0-9._-]{3,40}")) {
            throw new IllegalArgumentException(
                    "Username must be 3–40 characters using letters, numbers, dot, underscore or hyphen.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required.");
        }
        if (role == null || !SetHolder.ROLES.contains(role)) {
            throw new IllegalArgumentException("Choose a supported staff role.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String sql = """
                INSERT INTO staff_users
                    (username, password_hash, password_salt, display_name, role, active)
                VALUES (?, ?, ?, ?, ?, 1)
                """;
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql,
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username.trim().toLowerCase(java.util.Locale.ROOT));
            ps.setString(2, hash(password, salt));
            ps.setString(3, java.util.Base64.getEncoder().encodeToString(salt));
            ps.setString(4, displayName.trim());
            ps.setString(5, role);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("The staff account was saved but its identifier was not returned.");
    }

    /** Enables or disables a staff account without deleting its audit history. */
    public void setStaffUserActive(long staffUserId, boolean active) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE staff_users SET active = ? WHERE staff_user_id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, staffUserId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Staff account was not found.");
            }
        }
    }

    /** Replaces a staff password with a newly salted PBKDF2 hash. */
    public void resetStaffPassword(long staffUserId, String newPassword)
            throws SQLException {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE staff_users
                     SET password_hash = ?, password_salt = ?
                     WHERE staff_user_id = ?
                     """)) {
            ps.setString(1, hash(newPassword, salt));
            ps.setString(2, java.util.Base64.getEncoder().encodeToString(salt));
            ps.setLong(3, staffUserId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Staff account was not found.");
            }
        }
    }

    private static final class SetHolder {
        private static final java.util.Set<String> ROLES =
                java.util.Set.of("ADMIN", "SUPERVISOR", "WAITER", "CASHIER", "KITCHEN");
    }

    private static String hash(String value, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), salt, 120_000, 256);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return java.util.HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Password hashing is unavailable.", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
