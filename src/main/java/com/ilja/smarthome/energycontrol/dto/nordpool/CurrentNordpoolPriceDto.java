package com.ilja.smarthome.energycontrol.dto.nordpool;

import java.math.BigDecimal;

public record CurrentNordpoolPriceDto(String timestamp, BigDecimal price, BigDecimal hourlyAvgPrice) {}
