package com.okto.jalokduitnow.storage;

import java.time.Instant;

/** One "this already fired" record (reminder-log.json): the key is "reminder id @ period". */
public record ReminderLogDto(String key, Instant firedAt) {
}
