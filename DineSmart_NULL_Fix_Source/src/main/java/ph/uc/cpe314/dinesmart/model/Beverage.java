package ph.uc.cpe314.dinesmart.model;

public final class Beverage extends MenuItem {
    public Beverage(String name, double price, int prepMinutes) { super(name, price, prepMinutes); }
    @Override public String getItemType() { return "BEVERAGE"; }
}
