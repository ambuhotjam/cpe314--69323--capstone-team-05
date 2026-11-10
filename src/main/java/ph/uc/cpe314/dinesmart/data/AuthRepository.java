package ph.uc.cpe314.dinesmart.data;

import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;

/** Local account authentication for the desktop prototype. */
public final class AuthRepository {
    public record User(String username, String displayName, String role) { }
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
        if (username == null || password == null) return null;
        String sql = "SELECT username,display_name,role,password_hash,password_salt FROM staff_users WHERE username=? AND active=1";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                byte[] salt = java.util.Base64.getDecoder().decode(rs.getString("password_salt"));
                byte[] expected = java.util.HexFormat.of().parseHex(rs.getString("password_hash"));
                byte[] actual = java.util.HexFormat.of().parseHex(hash(password, salt));
                if (!MessageDigest.isEqual(expected, actual)) return null;
                return new User(rs.getString("username"), rs.getString("display_name"), rs.getString("role"));
            }
        }
    }

    private static String hash(String value, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), salt, 120_000, 256);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return java.util.HexFormat.of().formatHex(encoded);
        } catch (Exception e) { throw new IllegalStateException("Password hashing is unavailable", e); }
        finally { spec.clearPassword(); }
    }
}
