package com.okto.jalokduitnow.storage;

import java.util.List;
import java.util.UUID;

/** JSON form of the donor settings (donor-settings.json): which income categories count as donor income. */
public record DonorSettingsDto(List<UUID> donorCategoryIds) {
}
