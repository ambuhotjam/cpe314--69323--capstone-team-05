package ph.uc.cpe314.dinesmart.model;

/** Immutable sellable menu item with its price stored as exact whole cents. */
public abstract class MenuItem {
    private final String name;
    private final long priceCents;
    private final int basePrepMinutes;

    protected MenuItem(String name, long priceCents, int basePrepMinutes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (priceCents < 0) {
            throw new IllegalArgumentException("Price must be non-negative.");
        }
        if (basePrepMinutes < 0) {
            throw new IllegalArgumentException("Preparation time cannot be negative.");
        }
        this.name = name.trim();
        this.priceCents = priceCents;
        this.basePrepMinutes = basePrepMinutes;
    }

    public String getName() {
        return name;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public int getBasePrepMinutes() {
        return basePrepMinutes;
    }

    public abstract String getItemType();

    @Override
    public String toString() {
        return name + " (" + getItemType() + ") - ₱"
                + ph.uc.cpe314.dinesmart.util.Money.formatCents(priceCents);
    }
}
