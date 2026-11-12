package ph.uc.cpe314.dinesmart.model;

/** Main Course menu item. */
public final class MainCourse extends MenuItem {
    public MainCourse(String name, long priceCents, int prepMinutes) {
        super(name, priceCents, prepMinutes);
    }

    @Override
    public String getItemType() {
        return "MAIN_COURSE";
    }
}
