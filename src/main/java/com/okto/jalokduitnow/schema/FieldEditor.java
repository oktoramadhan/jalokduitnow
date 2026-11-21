package com.okto.jalokduitnow.schema;

import com.okto.jalokduitnow.exception.InvalidColumnException;
import javafx.scene.control.Control;

/** A JavaFX control paired with the typed value it edits. Created by {@link FieldType#createEditor()}. */
public interface FieldEditor<T> {
    Control control();

    /** The typed value, or null when the editor is empty. */
    T getValue() throws InvalidColumnException;

    /** Shows a value; null clears the editor. */
    void setValue(T value);
}
