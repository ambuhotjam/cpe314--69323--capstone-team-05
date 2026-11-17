package ph.uc.cpe314.dinesmart.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PendingOrderStackTest {
    @Test void popsNewestPendingOrderFirst() {
        PendingOrderStack stack = new PendingOrderStack();
        stack.rebuild(List.of("DS-OLD", "DS-MIDDLE", "DS-NEW"));
        assertEquals("DS-NEW", stack.popMostRecent().orElseThrow());
        assertEquals("DS-MIDDLE", stack.popMostRecent().orElseThrow());
    }

    @Test void emptyStackReturnsEmpty() {
        assertTrue(new PendingOrderStack().popMostRecent().isEmpty());
    }

    @Test void rebuildReplacesPreviousEntries() {
        PendingOrderStack stack = new PendingOrderStack();
        stack.rebuild(List.of("DS-1", "DS-2"));
        stack.rebuild(List.of("DS-3"));
        assertEquals(1, stack.size());
        assertEquals("DS-3", stack.popMostRecent().orElseThrow());
    }
}
