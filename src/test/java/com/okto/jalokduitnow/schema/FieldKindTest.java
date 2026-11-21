package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FieldKindTest {
    @Test
    void everyKindCreatesItsFieldTypeAndMapsBack() throws Exception {
        assertInstanceOf(TextType.class, FieldKind.TEXT.create(List.of()));
        assertInstanceOf(NumberType.class, FieldKind.NUMBER.create(List.of()));
        assertInstanceOf(DateType.class, FieldKind.DATE.create(List.of()));
        assertInstanceOf(BooleanType.class, FieldKind.YES_NO.create(List.of()));
        FieldType<?> choice = FieldKind.CHOICE.create(List.of("A", "B"));
        assertInstanceOf(ChoiceType.class, choice);
        assertEquals(List.of("A", "B"), choice.options());

        for (FieldKind k : FieldKind.values()) {
            assertEquals(k, FieldKind.of(k.create(List.of("x"))));
        }
    }

    @Test
    void onlyChoiceNeedsOptionsAndRejectsNone() {
        assertTrue(FieldKind.CHOICE.needsOptions());
        assertFalse(FieldKind.TEXT.needsOptions());
        assertThrows(InvalidColumnException.class, () -> FieldKind.CHOICE.create(List.of()));
        assertThrows(InvalidColumnException.class, () -> FieldKind.CHOICE.create(List.of("A", "a")));
    }

    @Test
    void labelsAreIndonesianAndFiveKindsExist() {
        assertEquals(5, FieldKind.values().length);
        assertEquals("Ya/Tidak", FieldKind.YES_NO.label());
        for (FieldKind k : FieldKind.values()) assertFalse(k.iconKey().isBlank());
    }
}
