package ph.uc.cpe314.dinesmart;

import org.junit.jupiter.api.Test;
import ph.uc.cpe314.dinesmart.data.ComboRepository;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ComboRepositoryTest {
    private final ComboRepository repository = new ComboRepository();

    @Test
    void rejectsEmptyComboComponents() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.createCombo("Empty Combo", Map.of(), 10));
    }

    @Test
    void rejectsInvalidDiscount() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.createCombo("Invalid Discount", Map.of("Item", 1), 100));
    }

    @Test
    void rejectsNonPositiveComponentQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.createCombo("Invalid Quantity", Map.of("Item", 0), 0));
    }
}
