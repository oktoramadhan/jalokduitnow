package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Each FieldType builds the matching control and reads its value back as the right Java type. */
class FieldEditorTest {
    @FunctionalInterface
    interface FxBody { void run() throws Exception; }

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyRunning) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start");
    }

    /** JavaFX controls must be used on the FX thread; assertion errors are rethrown to JUnit. */
    private static void onFx(FxBody body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() instanceof Exception e) throw e;
        if (failure.get() instanceof Error e) throw e;
    }

    @Test
    void textAndNumberUseTextFields() throws Exception {
        onFx(() -> {
            var text = new TextType().createEditor();
            assertInstanceOf(TextField.class, text.control());
            assertNull(text.getValue());
            text.setValue("  halo ");
            assertEquals("halo", text.getValue());

            var number = new NumberType().createEditor();
            assertInstanceOf(TextField.class, number.control());
            number.setValue(25000L);
            assertEquals(25000L, number.getValue());
            ((TextField) number.control()).setText("abc");
            assertThrows(InvalidColumnException.class, number::getValue);
        });
    }

    @Test
    void dateUsesDatePicker() throws Exception {
        onFx(() -> {
            var date = new DateType().createEditor();
            assertInstanceOf(DatePicker.class, date.control());
            date.setValue(LocalDate.of(2025, 8, 1));
            assertEquals(LocalDate.of(2025, 8, 1), date.getValue());
        });
    }

    @Test
    void choiceAndBooleanUseComboBoxesWithABlankOption() throws Exception {
        onFx(() -> {
            var choice = new ChoiceType(List.of("Need", "Want")).createEditor();
            assertInstanceOf(ComboBox.class, choice.control());
            assertNull(choice.getValue());
            choice.setValue("Want");
            assertEquals("Want", choice.getValue());

            var yesNo = new BooleanType().createEditor();
            assertInstanceOf(ComboBox.class, yesNo.control());
            assertNull(yesNo.getValue());
            yesNo.setValue(false);
            assertEquals(false, yesNo.getValue());
        });
    }
}
