package ph.uc.cpe314.dinesmart.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test
    void parsesAndFormatsExactCents() {
        assertEquals(929L, Money.parseCents("9.29"));
        assertEquals("9.29", Money.formatCents(929L));
        assertEquals("1,234.56", Money.formatGroupedCents(123456L));
    }

    @Test
    void rejectsMoreThanTwoFractionalDigitsAndNegativeAmounts() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.parseCents("9.299"));
        assertThrows(IllegalArgumentException.class,
                () -> Money.parseCents("-0.01"));
    }
}
