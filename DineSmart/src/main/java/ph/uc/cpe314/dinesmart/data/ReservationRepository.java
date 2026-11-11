package ph.uc.cpe314.dinesmart.data;
import java.sql.*;
import java.util.*;

/** Guest reservation book for the restaurant host team. */
public final class ReservationRepository {
    public record Reservation(long id,String guest,int partySize,String when,String table,String status,String notes) {
        @Override public String toString(){return "#" + id + " · " + guest + " · " + partySize + " guests · " + when + " · " + table + " · " + status;}
    }
    public long create(String guest,String phone,int partySize,String dateTime,String table,String notes) throws SQLException {
        if(guest==null || guest.isBlank() || partySize<1 || dateTime==null || dateTime.isBlank()) throw new IllegalArgumentException("Guest name, party size, and reservation date/time are required.");
        String reservationAt = dateTime.trim().replace('T', ' ');
        String tableLabel = table == null || table.isBlank() ? "Unassigned" : table.trim();
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                // A named table can only have one active booking in the same time slot.
                // Unassigned reservations are intentionally allowed to coexist.
                if (!"Unassigned".equalsIgnoreCase(tableLabel)) {
                    try (PreparedStatement conflict = c.prepareStatement("""
                            SELECT 1 FROM table_reservations
                            WHERE reservation_at = ?
                              AND table_label = ? COLLATE NOCASE
                              AND status IN ('BOOKED','SEATED')
                            LIMIT 1
                            """)) {
                        conflict.setString(1, reservationAt);
                        conflict.setString(2, tableLabel);
                        try (ResultSet rs = conflict.executeQuery()) {
                            if (rs.next()) {
                                throw new IllegalStateException("That table is already reserved for the selected time.");
                            }
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO table_reservations(guest_name,guest_phone,party_size,reservation_at,table_label,notes) VALUES(?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, guest.trim());
                    ps.setString(2, phone == null ? "" : phone.trim());
                    ps.setInt(3, partySize);
                    ps.setString(4, reservationAt);
                    ps.setString(5, tableLabel);
                    ps.setString(6, notes == null ? "" : notes.trim());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        long id = rs.next() ? rs.getLong(1) : -1;
                        c.commit();
                        return id;
                    }
                }
            } catch (Exception ex) {
                c.rollback();
                if (ex instanceof SQLException sqlException) throw sqlException;
                if (ex instanceof IllegalArgumentException illegalArgument) throw illegalArgument;
                if (ex instanceof IllegalStateException illegalState) throw illegalState;
                throw new SQLException("Could not create reservation.", ex);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
    public List<Reservation> findUpcoming() throws SQLException {
        List<Reservation> rows=new ArrayList<>();
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement(
            "SELECT reservation_id,guest_name,party_size,reservation_at,table_label,status,notes FROM table_reservations WHERE status IN ('BOOKED','SEATED') ORDER BY reservation_at"); ResultSet rs=ps.executeQuery()) {
            while(rs.next()) rows.add(new Reservation(rs.getLong(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)));
        }
        return rows;
    }
    public void updateStatus(long id,String status) throws SQLException {
        if(!List.of("BOOKED","SEATED","CANCELLED","COMPLETED").contains(status)) throw new IllegalArgumentException("Invalid reservation status.");
        try(Connection c=Database.connect(); PreparedStatement ps=c.prepareStatement("UPDATE table_reservations SET status=? WHERE reservation_id=?")) {
            ps.setString(1,status); ps.setLong(2,id); if(ps.executeUpdate()!=1) throw new SQLException("Reservation not found.");
        }
    }
}
