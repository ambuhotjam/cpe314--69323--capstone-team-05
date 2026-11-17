package ph.uc.cpe314.dinesmart.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeMeasureParserTest {
    @Test void parsesWholeAndDecimalMeasures() {
        assertEquals(150d, RecipeMeasureParser.parse("150 g").quantity(), 0.0001);
        assertEquals("ml", RecipeMeasureParser.parse("20 milliliters").unit());
    }
    @Test void parsesFractionsAndMixedFractions() {
        assertEquals(0.5d, RecipeMeasureParser.parse("1/2 cup").quantity(), 0.0001);
        assertEquals(1.5d, RecipeMeasureParser.parse("1 1/2 tbsp").quantity(), 0.0001);
    }
    @Test void descriptivePreparationWordsAreAcceptedButNotAutomaticallyDeductible() {
        assertNull(RecipeMeasureParser.parse("1 large"));
        assertNull(RecipeMeasureParser.parse("1 chopped"));
        assertNull(RecipeMeasureParser.parse("2 minced"));
        assertNull(RecipeMeasureParser.parse("1 crushed"));
    }
    @Test void acceptsCommonMeasurementAbbreviationsAndUnits() {
        assertEquals("tbsp", RecipeMeasureParser.parse("1 tbs").unit());
        assertEquals("oz", RecipeMeasureParser.parse("4 ounces").unit());
        assertEquals("lb", RecipeMeasureParser.parse("2 lbs").unit());
        assertEquals("fl oz", RecipeMeasureParser.parse("8 fl oz").unit());
        assertEquals("qt", RecipeMeasureParser.parse("1 quart").unit());
        assertEquals("gal", RecipeMeasureParser.parse("1 gallon").unit());
        assertEquals(16d, RecipeMeasureParser.convert(1, "lb", "oz"), 0.0001);
    }
    @Test void vagueAndUnsupportedMeasuresAreAcceptedButNotAutomaticallyDeductible() {
        assertNull(RecipeMeasureParser.parse("to taste"));
        assertNull(RecipeMeasureParser.parse("as needed"));
        assertNull(RecipeMeasureParser.parse("one handful"));
        assertFalse(RecipeMeasureParser.isAutomaticallyDeductible(null, null));
        assertFalse(RecipeMeasureParser.isAutomaticallyDeductible(null, "g"));
        assertFalse(RecipeMeasureParser.isAutomaticallyDeductible(1d, "handful"));
        assertTrue(RecipeMeasureParser.isAutomaticallyDeductible(150d, "g"));
    }
    @Test void convertsSupportedUnitsAndRejectsCrossDimensions() {
        assertEquals(1000d, RecipeMeasureParser.convert(1, "kg", "g"), 0.0001);
        assertEquals(1000d, RecipeMeasureParser.convert(1, "l", "ml"), 0.0001);
        assertTrue(RecipeMeasureParser.canConvert("tbsp", "ml"));
        assertFalse(RecipeMeasureParser.canConvert("kg", "ml"));
        assertThrows(IllegalArgumentException.class, () -> RecipeMeasureParser.convert(1, "kg", "ml"));
    }
}
