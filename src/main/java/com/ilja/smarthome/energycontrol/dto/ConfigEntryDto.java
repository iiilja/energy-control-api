package com.ilja.smarthome.energycontrol.dto;

/** One {@code system_configuration} entry; {@code updatedAt} is an ISO date-time with offset. */
public record ConfigEntryDto(String key, String value, String description, String updatedAt) {
}
