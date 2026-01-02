package com.ilja.smarthome.energycontrol.dto.elering;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EleringPriceResponse {
    private boolean success;
    private Map<String, List<PriceEntry>> data;

    @Data
    public static class PriceEntry {
        private Long timestamp;
        private Double price;
    }
}
