package com.okto.jalokduitnow.util;

import com.okto.jalokduitnow.exception.InvalidRecipientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberNormalizerTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "081234567890            | 6281234567890",   // plain local number: leading 0 becomes 62
            "0812-3456-7890          | 6281234567890",   // dashes
            "0812 3456 7890          | 6281234567890",   // spaces
            "(0812) 3456 7890        | 6281234567890",   // parentheses
            "0812.3456.7890          | 6281234567890",   // dots
            "+6281234567890          | 6281234567890",   // plus sign
            "+62 812 3456 7890       | 6281234567890",
            "+62-812-3456-7890       | 6281234567890",
            "62 812-3456-7890        | 6281234567890",   // already international, no plus
            "6281234567890           | 6281234567890",
            "0062 812 3456 7890      | 6281234567890",   // 00 international call prefix
            "+62 0812 3456 7890      | 6281234567890",   // country code followed by the local zero
            "   0812 3456 7890       | 6281234567890",   // surrounding whitespace
            "08111222333             | 628111222333",    // shorter valid number
            "+1 (415) 555-2671       | 14155552671",     // another country is left alone
            "+44 20 7946 0958        | 442079460958"
    })
    void normalizesCommonFormats(String raw, String expected) throws Exception {
        assertEquals(expected, PhoneNumberNormalizer.normalize(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0812-ABC-7890", "0812 3456 78x0", "08123456!890", "+62 812 3456 789a"})
    void rejectsNonNumericInput(String raw) {
        InvalidRecipientException e = assertThrows(InvalidRecipientException.class, () -> PhoneNumberNormalizer.normalize(raw));
        assertTrue(e.getMessage().contains("angka"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "12345", "0812", "08123456", "123456789"})
    void rejectsNumbersThatAreTooShort(String raw) {
        InvalidRecipientException e = assertThrows(InvalidRecipientException.class, () -> PhoneNumberNormalizer.normalize(raw));
        assertTrue(e.getMessage().contains("pendek"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0812345678901234567", "1234567890123456", "+62 812 3456 7890 1234 5678"})
    void rejectsNumbersThatAreTooLong(String raw) {
        InvalidRecipientException e = assertThrows(InvalidRecipientException.class, () -> PhoneNumberNormalizer.normalize(raw));
        assertTrue(e.getMessage().contains("panjang"));
    }

    @Test
    void lengthBoundaries() throws Exception {
        assertEquals("1234567890", PhoneNumberNormalizer.normalize("1234567890"));            // 10: shortest accepted
        assertEquals("123456789012345", PhoneNumberNormalizer.normalize("+123456789012345")); // 15: longest accepted
        assertThrows(InvalidRecipientException.class, () -> PhoneNumberNormalizer.normalize("123456789"));
        assertThrows(InvalidRecipientException.class, () -> PhoneNumberNormalizer.normalize("1234567890123456"));
    }

    @Test
    void blankInputIsRefused() {
        assertThrows(InvalidRecipientException.class, () -> PhoneNumberNormalizer.normalize(null));
        assertThrows(InvalidRecipientException.class, () -> PhoneNumberNormalizer.normalize(""));
        assertThrows(InvalidRecipientException.class, () -> PhoneNumberNormalizer.normalize("   "));
        assertThrows(InvalidRecipientException.class, () -> PhoneNumberNormalizer.normalize("+ - ()"));  // nothing left after stripping
    }

    @Test
    void theResultIsAlwaysDigitsOnlyAndStable() throws Exception {
        String once = PhoneNumberNormalizer.normalize("+62 (0)812-3456-7890");
        assertTrue(once.matches("[0-9]+"));
        assertEquals(once, PhoneNumberNormalizer.normalize(once));    // normalizing again changes nothing
    }
}
