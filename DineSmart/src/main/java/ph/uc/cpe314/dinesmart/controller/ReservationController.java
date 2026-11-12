package ph.uc.cpe314.dinesmart.controller;

import java.sql.SQLException;
import java.util.List;
import ph.uc.cpe314.dinesmart.data.ReservationRepository;
import ph.uc.cpe314.dinesmart.service.StaffPermissions;

/** Coordinates reservation operations and protects them with staff-role checks. */
public final class ReservationController {
    private final ReservationRepository repository;

    public ReservationController(ReservationRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    public List<ReservationRepository.Reservation> list(String role)
            throws SQLException {
        require(role);
        return repository.findUpcoming();
    }

    public long book(String role, String guest, String phone, int partySize,
                     String dateTime, String tableLabel, String notes)
            throws SQLException {
        require(role);
        return repository.create(guest, phone, partySize, dateTime, tableLabel, notes);
    }

    public void updateStatus(String role, long reservationId, String status)
            throws SQLException {
        require(role);
        repository.updateStatus(reservationId, status);
    }

    private static void require(String role) {
        if (!StaffPermissions.canManageReservations(role)) {
            throw new SecurityException("Your staff role cannot manage reservations.");
        }
    }
}
