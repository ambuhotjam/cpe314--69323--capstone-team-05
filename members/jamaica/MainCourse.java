package ph.uc.cpe314.dinesmart.model;

public final class MainCourse extends MenuItem {
    public MainCourse(String name, double price, int prepMinutes) { super(name, price, prepMinutes); }
    @Override public String getItemType() { return "MAIN_COURSE"; }
}
