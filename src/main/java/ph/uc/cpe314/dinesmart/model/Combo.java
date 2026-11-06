package ph.uc.cpe314.dinesmart.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class Combo {
    public record Component(MenuItem item, Combo childCombo, int quantity) {
        public Component {
            if ((item == null) == (childCombo == null))
                throw new IllegalArgumentException("A component must reference exactly one menu item or child combo.");
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive.");
        }
    }

    private final String name;
    private final List<Component> components = new ArrayList<>();

    public Combo(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Combo name is required.");
        this.name = name;
    }

    public String getName() { return name; }
    public List<Component> getComponents() { return Collections.unmodifiableList(components); }

    public void addItem(MenuItem item, int quantity) {
        components.add(new Component(item, null, quantity));
    }

    public void addCombo(Combo child, int quantity) {
        if (child == null) throw new IllegalArgumentException("Child combo is required.");
        if (child == this || child.contains(this, Collections.newSetFromMap(new IdentityHashMap<>())))
            throw new IllegalArgumentException("A combo cannot contain itself directly or indirectly.");
        components.add(new Component(null, child, quantity));
    }

    private boolean contains(Combo target, Set<Combo> visited) {
        if (this == target) return true;
        if (!visited.add(this)) return false;
        for (Component component : components) {
            if (component.childCombo() != null && component.childCombo().contains(target, visited)) return true;
        }
        return false;
    }

    public double getTotalPrice() {
        return totalPrice(Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private double totalPrice(Set<Combo> activePath) {
        // Base case: a combo with no components costs zero.
        if (components.isEmpty()) return 0.0;
        if (!activePath.add(this)) throw new IllegalStateException("Cycle detected in combo structure.");
        double total = 0.0;
        try {
            for (Component component : components) {
                double unitPrice = component.item() != null
                    ? component.item().getPrice()
                    : component.childCombo().totalPrice(activePath); // recursive step
                total += unitPrice * component.quantity();
            }
            return total;
        } finally {
            activePath.remove(this);
        }
    }
}
