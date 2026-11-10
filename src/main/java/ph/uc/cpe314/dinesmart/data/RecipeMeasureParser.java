package ph.uc.cpe314.dinesmart.data;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses common recipe measures without inventing quantities for vague measures. */
public final class RecipeMeasureParser {
    private static final Pattern AMOUNT = Pattern.compile("^\\s*(\\d+\\s+\\d+\\s*/\\s*\\d+|\\d+\\s*/\\s*\\d+|\\d+(?:\\.\\d+)?)\\s*(.*)$");
    public record Parsed(double quantity, String unit) {}
    private RecipeMeasureParser() {}

    public static Parsed parse(String measure) {
        if (measure == null || measure.isBlank()) return null;
        Matcher m = AMOUNT.matcher(measure.trim());
        if (!m.matches()) return null;
        double amount;
        try { amount = fraction(m.group(1)); } catch (RuntimeException ex) { return null; }
        if (!Double.isFinite(amount) || amount <= 0) return null;
        String rawUnit = m.group(2).trim().toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        String unit = canonicalUnit(rawUnit);
        return unit == null ? null : new Parsed(amount, unit);
    }

    private static double fraction(String value) {
        String v = value.trim();
        if (v.contains(" ")) {
            String[] parts = v.split("\\s+");
            return Double.parseDouble(parts[0]) + fraction(parts[1]);
        }
        if (v.contains("/")) { String[] parts = v.split("/"); return Double.parseDouble(parts[0].trim()) / Double.parseDouble(parts[1].trim()); }
        return Double.parseDouble(v);
    }

    /**
     * Returns true only when a recipe measurement can safely participate in
     * automatic inventory deduction. Descriptive measures (for example,
     * "to taste", "1 handful", or "juice of 3 limes") remain valid recipe
     * instructions, but must never be converted into guessed stock quantities.
     */
    public static boolean isAutomaticallyDeductible(Double quantity, String unit) {
        return quantity != null && Double.isFinite(quantity) && quantity > 0
                && unit != null && canonicalUnit(unit.trim().toLowerCase(Locale.ROOT)) != null;
    }

    public static boolean canConvert(String from, String to) {
        String a = canonicalUnit(from == null ? "" : from.trim().toLowerCase(Locale.ROOT));
        String b = canonicalUnit(to == null ? "" : to.trim().toLowerCase(Locale.ROOT));
        return a != null && b != null && dimension(a).equals(dimension(b));
    }

    /** Converts supported mass/volume units to the target unit; count-like units convert only to themselves. */
    public static double convert(double quantity, String from, String to) {
        if (!Double.isFinite(quantity) || quantity < 0 || !canConvert(from, to))
            throw new IllegalArgumentException("Cannot convert " + from + " to " + to + ".");
        String a = canonicalUnit(from.trim().toLowerCase(Locale.ROOT));
        String b = canonicalUnit(to.trim().toLowerCase(Locale.ROOT));
        return quantity * factor(a) / factor(b);
    }

    private static String dimension(String u) {
        return switch (u) {
            case "g", "kg", "mg", "oz", "lb" -> "MASS";
            case "ml", "l", "tsp", "tbsp", "cup", "fl oz", "qt", "gal" -> "VOLUME";
            default -> "COUNT:" + u;
        };
    }

    private static double factor(String u) {
        return switch (u) {
            case "kg" -> 1000d; case "g" -> 1d; case "mg" -> 0.001d; case "lb" -> 453.59237d; case "oz" -> 28.349523125d;
            case "l" -> 1000d; case "ml" -> 1d; case "tsp" -> 4.92892d;
            case "tbsp" -> 14.7868d; case "cup" -> 236.588d; case "fl oz" -> 29.5735d; case "qt" -> 946.353d; case "gal" -> 3785.41d;
            default -> 1d;
        };
    }

    private static String canonicalUnit(String u) {
        if (u.isBlank() || u.equals("to taste") || u.equals("as needed")) return null;
        return switch (u) {
            case "g", "gram", "grams" -> "g";
            case "kg", "kilogram", "kilograms" -> "kg";
            case "oz", "ounce", "ounces" -> "oz";
            case "lb", "lbs", "pound", "pounds" -> "lb";
            case "mg", "milligram", "milligrams" -> "mg";
            case "ml", "milliliter", "milliliters", "millilitre", "millilitres" -> "ml";
            case "fl oz", "fluid ounce", "fluid ounces" -> "fl oz";
            case "qt", "quart", "quarts" -> "qt";
            case "gal", "gallon", "gallons" -> "gal";
            case "l", "liter", "liters", "litre", "litres" -> "l";
            case "tsp", "teaspoon", "teaspoons" -> "tsp";
            case "tbs", "tbsp", "tablespoon", "tablespoons" -> "tbsp";
            case "cup", "cups" -> "cup";
            case "clove", "cloves", "piece", "pieces", "pc", "pcs", "each" -> "pcs";
            case "bottle", "bottles" -> "bottle";
            case "packet", "packets", "pack" -> "packet";
            case "stick", "sticks" -> "stick";
            case "head", "heads" -> "head";
            case "stalk", "stalks" -> "stalk";
            case "sprig", "sprigs" -> "sprig";
            case "pinch", "pinches" -> "pinch";
            case "can", "cans", "tin", "tins" -> "can";
            case "bunch", "bunches" -> "bunch";
            case "slice", "slices" -> "slice";
            default -> null;
        };
    }
}
