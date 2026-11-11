package ph.uc.cpe314.dinesmart.data;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Stores supervisor suggestions for manager/administrator review. */
public final class MenuRecommendationRepository {
    public record Recommendation(int id, String item, String reason, String suggestedBy,
                                 String status, String createdAt) {
        @Override public String toString() {
            return item + "  |  " + status + "  |  by " + suggestedBy
                    + (reason == null || reason.isBlank() ? "" : "\\n" + reason)
                    + "  |  " + createdAt;
        }
    }

    public void submit(String item, String reason, String suggestedBy) throws Exception {
        if (item == null || item.isBlank())
            throw new IllegalArgumentException("Enter the name of the dish you want to recommend.");
        if (suggestedBy == null || suggestedBy.isBlank())
            throw new IllegalArgumentException("Your staff account name is missing. Please sign in again.");
        String sql = "INSERT INTO menu_recommendations(suggested_item,reason,suggested_by) VALUES(?,?,?)";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, item.trim());
            ps.setString(2, reason == null ? "" : reason.trim());
            ps.setString(3, suggestedBy.trim());
            ps.executeUpdate();
        }
    }

    public List<Recommendation> findAll() throws Exception {
        List<Recommendation> rows = new ArrayList<>();
        String sql = "SELECT recommendation_id,suggested_item,reason,suggested_by,status,created_at "
                + "FROM menu_recommendations ORDER BY recommendation_id DESC";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Recommendation(rs.getInt(1), rs.getString(2),
                    rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
        }
        return rows;
    }
}
