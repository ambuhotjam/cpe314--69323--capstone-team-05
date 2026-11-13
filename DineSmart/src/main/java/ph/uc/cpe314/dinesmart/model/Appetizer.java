package ph.uc.cpe314.dinesmart.model;

/** Appetizer menu item. */
public final class Appetizer extends MenuItem {
    public Appetizer(String name, long priceCents, int prepMinutes) {
        super(name, priceCents, prepMinutes);
    }

    @Override
    public String getItemType() {
        return "APPETIZER";
    }
}

