package ph.uc.cpe314.dinesmart.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Guest reservation book linked to the restaurant's master table list. */
public final class ReservationRepository {
    public record Reservation(long id, String guest, int partySize, String when,
                              String table, Long tableId, String status, String notes) {
        @Override
        public String toString() {
            return "#" + id + " · " + guest + " · " + partySize
                    + " guests · " + when + " · " + table + " · " + status;
        }
    }

    /** Saves a reservation and resolves its table label to a restaurant_tables row. */
    public long create(String guest, String phone, int partySize, String dateTime,
                       String table, String notes) throws SQLException {
        if (guest == null || guest.isBlank() || partySize < 1
                || dateTime == null || dateTime.isBlank()) {
            throw new IllegalArgumentException(
                    "Guest name, party size, and reservation date/time are required.");
        }

        String reservationAt = dateTime.trim().replace('T', ' ');
        String tableLabel = table == null || table.isBlank()
                ? "Unassigned" : table.trim();

        try (Connection connection = Database.connect()) {
            connection.setAutoCommit(false);
            try {
                Long tableId = resolveTableId(connection, tableLabel);
                if (tableId != null) {
                    try (PreparedStatement conflict = connection.prepareStatement("""
                            SELECT 1
                            FROM table_reservations
                            WHERE reservation_at = ?
                              AND table_id = ?
                              AND status IN ('BOOKED','SEATED')
                            LIMIT 1
                            """)) {
                        conflict.setString(1, reservationAt);
                        conflict.setLong(2, tableId);
                        try (ResultSet rs = conflict.executeQuery()) {
                            if (rs.next()) {
                                throw new IllegalStateException(
                                        "That table is already reserved for the selected time.");
                            }
                        }
                    }
                }

                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO table_reservations
                            (guest_name, guest_phone, party_size, reservation_at,
                             table_label, table_id, notes)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, guest.trim());
                    insert.setString(2, phone == null ? "" : phone.trim());
                    insert.setInt(3, partySize);
                    insert.setString(4, reservationAt);
                    insert.setString(5, tableLabel);
                    if (tableId == null) {
                        insert.setNull(6, Types.INTEGER);
                    } else {
                        insert.setLong(6, tableId);
                    }
                    insert.setString(7, notes == null ? "" : notes.trim());
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        long id = keys.next() ? keys.getLong(1) : -1;
                        connection.commit();
                        return id;
                    }
                }
            } catch (SQLException | RuntimeException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    ex.addSuppressed(rollbackFailure);
                }
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private Long resolveTableId(Connection connection, String tableLabel)
            throws SQLException {
        if ("Unassigned".equalsIgnoreCase(tableLabel)) {
            return null;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO restaurant_tables(table_label) VALUES(?)")) {
            insert.setString(1, tableLabel);
            insert.executeUpdate();
        }
        try (PreparedStatement find = connection.prepareStatement("""
                SELECT table_id
                FROM restaurant_tables
                WHERE table_label = ? COLLATE NOCASE
                """)) {
            find.setString(1, tableLabel);
            try (ResultSet rs = find.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Returns active bookings and their linked restaurant table. */
    public List<Reservation> findUpcoming() throws SQLException {
        List<Reservation> rows = new ArrayList<>();
        String sql = """
                SELECT reservation_id, guest_name, party_size, reservation_at,
                       table_label, table_id, status, notes
                FROM table_reservations
                WHERE status IN ('BOOKED','SEATED')
                ORDER BY reservation_at
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                long rawTableId = rs.getLong("table_id");
                Long tableId = rs.wasNull() ? null : rawTableId;
                rows.add(new Reservation(rs.getLong("reservation_id"),
                        rs.getString("guest_name"), rs.getInt("party_size"),
                        rs.getString("reservation_at"), rs.getString("table_label"),
                        tableId, rs.getString("status"), rs.getString("notes")));
            }
        }
        return rows;
    }

    /** Updates a reservation's lifecycle state. */
    public void updateStatus(long id, String status) throws SQLException {
        if (!List.of("BOOKED", "SEATED", "CANCELLED", "COMPLETED").contains(status)) {
            throw new IllegalArgumentException("Invalid reservation status.");
        }
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE table_reservations SET status = ? WHERE reservation_id = ?")) {
            statement.setString(1, status);
            statement.setLong(2, id);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Reservation not found.");
            }
        }
    }
}
