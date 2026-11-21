package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FieldTypeTest {
    @Test
    void textRejectsTooLong() throws Exception {
        assertEquals("halo", new TextType().parseValue("halo"));
        assertThrows(InvalidColumnException.class, () -> new TextType().parseValue("x".repeat(501)));
    }

    @Test
    void numberAcceptsThousandSeparators() throws Exception {
        assertEquals(25000L, new NumberType().parseValue("25.000"));
        assertEquals("25.000", new NumberType().displayValue(25000L));
        assertThrows(InvalidColumnException.class, () -> new NumberType().parseValue("abc"));
    }

    @Test
    void dateAcceptsIsoAndIndonesianFormatAndIsStrict() throws Exception {
        assertEquals(LocalDate.of(2025, 8, 1), new DateType().parseValue("01/08/2025"));
        assertEquals(LocalDate.of(2025, 8, 1), new DateType().parseValue("2025-08-01"));
        assertThrows(InvalidColumnException.class, () -> new DateType().parseValue("31/02/2025"));
    }

    @Test
    void booleanAcceptsYaTidak() throws Exception {
        assertEquals(true, new BooleanType().parseValue("Ya"));
        assertEquals(false, new BooleanType().parseValue("tidak"));
        assertEquals("Ya", new BooleanType().displayValue(true));
        assertThrows(InvalidColumnException.class, () -> new BooleanType().parseValue("mungkin"));
    }

    @Test
    void choiceMatchesIgnoringCaseAndRejectsUnknown() throws Exception {
        ChoiceType c = new ChoiceType(List.of("Need", "Want"));
        assertEquals("Need", c.parseValue("need"));
        assertThrows(InvalidColumnException.class, () -> c.parseValue("Other"));
        assertThrows(InvalidColumnException.class, () -> c.check("Other"));
    }

    @Test
    void choiceNeedsUniqueNonEmptyOptions() {
        assertThrows(InvalidColumnException.class, () -> new ChoiceType(List.of()));
        assertThrows(InvalidColumnException.class, () -> new ChoiceType(List.of("A", "a")));
    }

    @Test
    void checkRejectsWrongRuntimeClass() {
        assertThrows(InvalidColumnException.class, () -> new NumberType().check("12"));
        assertThrows(InvalidColumnException.class, () -> new DateType().check("2025-08-01"));
        assertThrows(InvalidColumnException.class, () -> new BooleanType().check(1));
    }

    @Test
    void formatValueIsTheStoredText() throws Exception {
        assertEquals("2025-08-01", new DateType().formatValue(LocalDate.of(2025, 8, 1)));
        assertEquals("true", new BooleanType().formatValue(true));
        assertEquals("1500", new NumberType().formatValue(1500L));
    }
}
