package ph.uc.cpe314.dinesmart;

import org.junit.jupiter.api.Test;
import ph.uc.cpe314.dinesmart.model.*;
import static org.junit.jupiter.api.Assertions.*;

class ComboTest {
    @Test void calculatesNestedComboPriceRecursively() {
        MenuItem rice = new MainCourse("Rice Meal", 120.0, 15);
        MenuItem juice = new Beverage("Juice", 40.0, 2);
        Combo inner = new Combo("Meal");
        inner.addItem(rice, 1);
        inner.addItem(juice, 2);
        Combo outer = new Combo("Family Set");
        outer.addCombo(inner, 2);
        assertEquals(400.0, outer.getTotalPrice(), 0.001);
    }

    @Test void rejectsZeroQuantityAsEdgeCase() {
        Combo combo = new Combo("Invalid Combo");
        MenuItem snack = new Appetizer("Snack", 50.0, 5);
        assertThrows(IllegalArgumentException.class, () -> combo.addItem(snack, 0));
    }

    @Test void rejectsIndirectComboCycle() {
        Combo a = new Combo("A");
        Combo b = new Combo("B");
        a.addCombo(b, 1);
        assertThrows(IllegalArgumentException.class, () -> b.addCombo(a, 1));
    }
}
