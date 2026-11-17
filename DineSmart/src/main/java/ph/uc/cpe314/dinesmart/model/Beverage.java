package ph.uc.cpe314.dinesmart.model;

/** Beverage menu item. */
public final class Beverage extends MenuItem {
    public Beverage(String name, long priceCents, int prepMinutes) {
        super(name, priceCents, prepMinutes);
    }

    @Override
    public String getItemType() {
        return "BEVERAGE";
    }
}

