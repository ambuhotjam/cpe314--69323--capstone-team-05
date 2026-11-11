package ph.uc.cpe314.dinesmart;

import org.junit.jupiter.api.Test;
import ph.uc.cpe314.dinesmart.data.MenuItemRepository;

import static org.junit.jupiter.api.Assertions.*;

class MenuItemRepositoryValidationTest {

    private final MenuItemRepository repository = new MenuItemRepository();

    @Test
    void insertRejectsNullMenuItem() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.insert(null));
    }

    @Test
    void updatePriceRejectsMissingName() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.updatePriceByName(" ", 100.0));
    }

    @Test
    void updatePriceRejectsZeroPrice() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.updatePriceByName("Garlic Bread", 0.0));
    }

    @Test
    void updatePriceRejectsNotANumber() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.updatePriceByName("Garlic Bread", Double.NaN));
    }
}
