package com.okto.jalokduitnow.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** The one Jackson configuration the app uses for its files and for backups, so they always read each other. */
public final class JsonSupport {
    private JsonSupport() {}

    public static ObjectMapper newMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS) // FieldType subclasses without fields
                .enable(SerializationFeature.INDENT_OUTPUT);
    }
}
