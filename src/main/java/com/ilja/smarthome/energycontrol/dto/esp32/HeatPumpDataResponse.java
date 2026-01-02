package com.ilja.smarthome.energycontrol.dto.esp32;

import lombok.Data;

import java.util.List;

@Data
public class HeatPumpDataResponse {
    private StatusDto status;
    private TemperaturesDto temperatures;
    private CompressorDto compressor;
    private HeatingDto heating;
    private HeatCurveDto heatCurve;
    private PumpDto pump;

    @Data
    public static class StatusDto {
        private Boolean connected;
        private Long lastUpdate;
        private Short operationMode;
        private String operationModeText;
        private Boolean alarmActive;
        private Boolean compressorRunning;
        private Short currentDemand;
        private String currentDemandText;
    }

    @Data
    public static class TemperaturesDto {
        private Double outdoor;
        private Double brineIn;
        private Double brineOut;
        private Double systemSupplyIn;
        private Double systemSupplyOut;
        private Double systemSupplyLine;
        private Double systemSupplySetpoint;
        private Double tapWaterTop;
        private Double tapWaterLower;
    }

    @Data
    public static class CompressorDto {
        private Integer rpm;
        private Integer speed;
        private Integer hours;
    }

    @Data
    public static class HeatingDto {
        private Double setpoint;
        private Integer hours;
        private Integer externalHeaterHours;
    }

    @Data
    public static class HeatCurveDto {
        private List<Double> outdoorTemp;
        private List<Double> supplyTemp;
    }

    @Data
    public static class PumpDto {
        private Boolean autoMode;
        private Boolean currentState;
        private Boolean manualState;
        private Integer onDuration;
        private Integer offDuration;
        private Long lastStateChange;
        private Integer remainingMinutes;
    }
}
