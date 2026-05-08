package com.ilja.smarthome.energycontrol.dto.heating;

import java.math.BigDecimal;

public record WeeklyScheduleEntryDto(Long id, Short dayOfWeek, String startTime, BigDecimal setpoint) {}
