package ph.uc.cpe314.dinesmart.model;

public final class Appetizer extends MenuItem {
    public Appetizer(String name, double price, int prepMinutes) { super(name, price, prepMinutes); }
    @Override public String getItemType() { return "APPETIZER"; }
}
