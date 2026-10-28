package ph.uc.cpe314.dinesmart.model;

public abstract class MenuItem {
    private final String name;
    private final double price;
    private final int basePrepMinutes;

    protected MenuItem(String name, double price, int basePrepMinutes) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required.");
        if (!Double.isFinite(price) || price < 0) throw new IllegalArgumentException("Price must be non-negative.");
        if (basePrepMinutes < 0) throw new IllegalArgumentException("Preparation time cannot be negative.");
        this.name = name;
        this.price = price;
        this.basePrepMinutes = basePrepMinutes;
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getBasePrepMinutes() { return basePrepMinutes; }
    public abstract String getItemType();

    @Override public String toString() {
        return name + " (" + getItemType() + ") - ₱" + String.format("%.2f", getPrice());
    }
}
