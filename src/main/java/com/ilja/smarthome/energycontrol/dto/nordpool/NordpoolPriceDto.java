package com.ilja.smarthome.energycontrol.dto.nordpool;

import java.math.BigDecimal;

public record NordpoolPriceDto(String timestamp, BigDecimal price) {}
