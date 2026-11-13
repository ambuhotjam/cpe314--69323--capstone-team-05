package ph.uc.cpe314.dinesmart.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Converts user-entered currency to exact whole cents.
 * Monetary calculations in DineSmart use integer cents; floating-point values
 * are reserved for physical quantities and layout dimensions.
 */
public final class Money {
    private static final int SCALE = 2;

    private Money() {
    }

    /**
     * Parses a decimal currency value with no more than two fractional digits.
     *
     * @param value user-entered amount, for example "129.50"
     * @return amount in whole cents
     * @throws IllegalArgumentException when the value is blank, malformed,
     *                                  negative, or has more than two decimals
     */
    public static long parseCents(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enter a price.");
        }
        try {
            BigDecimal amount = new BigDecimal(value.trim())
                    .setScale(SCALE, RoundingMode.UNNECESSARY);
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Price cannot be negative.");
            }
            return amount.movePointRight(SCALE).longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Enter a valid amount with no more than two decimal places.", ex);
        }
    }

    /**
     * Formats cents as a plain two-decimal amount.
     *
     * @param cents amount in whole cents
     * @return formatted amount without a currency symbol
     */
    public static String formatCents(long cents) {
        return BigDecimal.valueOf(cents, SCALE).setScale(SCALE).toPlainString();
    }

    /**
     * Formats cents with grouping separators for dashboard totals.
     *
     * @param cents amount in whole cents
     * @return grouped amount without a currency symbol
     */
    public static String formatGroupedCents(long cents) {
        return String.format(Locale.US, "%,.2f", BigDecimal.valueOf(cents, SCALE));
    }

    /**
     * Returns a decimal string for legacy SQLite REAL columns during migration.
     *
     * @param cents amount in whole cents
     * @return exact two-decimal text
     */
    public static String toDatabaseDecimal(long cents) {
        return formatCents(cents);
    }
}
