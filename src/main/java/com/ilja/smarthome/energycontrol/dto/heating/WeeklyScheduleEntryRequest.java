package com.ilja.smarthome.energycontrol.dto.heating;

import java.math.BigDecimal;

public record WeeklyScheduleEntryRequest(Short dayOfWeek, String startTime, BigDecimal setpoint) {}
