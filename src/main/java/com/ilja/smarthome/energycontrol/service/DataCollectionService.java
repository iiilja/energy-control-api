package com.ilja.smarthome.energycontrol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilja.smarthome.energycontrol.domain.model.*;
import com.ilja.smarthome.energycontrol.thermia.dto.HeatPumpDataResponse;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import com.ilja.smarthome.energycontrol.thermia.service.ThermiaHeatPumpService;
import com.ilja.smarthome.energycontrol.repository.HeatPumpReadingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataCollectionService {

    private final ThermiaHeatPumpService thermiaService;
    private final HeatPumpReadingRepository readingRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public DataCollectionService(
            ThermiaHeatPumpService thermiaService,
            HeatPumpReadingRepository readingRepository,
            ObjectMapper objectMapper) {
        this.thermiaService = thermiaService;
        this.readingRepository = readingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public HeatPumpReading collectAndStoreData() {
        log.debug("Starting data collection from Thermia heat pump");

        try {
            HeatPumpDataResponse response = thermiaService.getHeatPumpData();

            HeatPumpReading reading = mapResponseToReading(response);
            reading.setCollectionTimestamp(LocalDateTime.now());

            try {
                reading.setRawJson(objectMapper.writeValueAsString(response));
            } catch (Exception e) {
                log.warn("Failed to serialize response to JSON: {}", e.getMessage());
            }

            HeatPumpReading saved = readingRepository.save(reading);

            log.info("Successfully collected and stored reading with ID: {}", saved.getId());
            return saved;

        } catch (ThermiaCommException e) {
            log.error("Failed to communicate with Thermia heat pump: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during data collection", e);
            throw new RuntimeException("Data collection failed: " + e.getMessage(), e);
        }
    }

    private HeatPumpReading mapResponseToReading(HeatPumpDataResponse response) {
        HeatPumpReading reading = new HeatPumpReading();

        reading.setStatus(mapStatusData(response.getStatus()));
        reading.setTemperatures(mapTemperatureData(response.getTemperatures()));
        reading.setCompressor(mapCompressorData(response.getCompressor()));
        reading.setHeating(mapHeatingData(response.getHeating()));
        reading.setHeatCurve(mapHeatCurveData(response.getHeatCurve()));

        return reading;
    }

    private StatusData mapStatusData(HeatPumpDataResponse.StatusDto dto) {
        if (dto == null) return null;

        StatusData status = new StatusData();
        status.setConnected(dto.getConnected());
        status.setLastUpdate(dto.getLastUpdate());
        status.setOperationMode(dto.getOperationMode());
        status.setOperationModeText(dto.getOperationModeText());
        status.setAlarmActive(dto.getAlarmActive());
        status.setCompressorRunning(dto.getCompressorRunning());
        status.setCurrentDemand(dto.getCurrentDemand());
        status.setCurrentDemandText(dto.getCurrentDemandText());

        return status;
    }

    private TemperatureData mapTemperatureData(HeatPumpDataResponse.TemperaturesDto dto) {
        if (dto == null) return null;

        TemperatureData temp = new TemperatureData();
        temp.setOutdoorTemp(toBigDecimal(dto.getOutdoor()));
        temp.setBrineInTemp(toBigDecimal(dto.getBrineIn()));
        temp.setBrineOutTemp(toBigDecimal(dto.getBrineOut()));
        temp.setSystemSupplyInTemp(toBigDecimal(dto.getSystemSupplyIn()));
        temp.setSystemSupplyOutTemp(toBigDecimal(dto.getSystemSupplyOut()));
        temp.setSystemSupplyLineTemp(toBigDecimal(dto.getSystemSupplyLine()));
        temp.setSystemSupplySetpoint(toBigDecimal(dto.getSystemSupplySetpoint()));
        temp.setTapWaterTopTemp(toBigDecimal(dto.getTapWaterTop()));
        temp.setTapWaterLowerTemp(toBigDecimal(dto.getTapWaterLower()));

        return temp;
    }

    private CompressorData mapCompressorData(HeatPumpDataResponse.CompressorDto dto) {
        if (dto == null) return null;

        CompressorData compressor = new CompressorData();
        compressor.setRpm(dto.getRpm());
        compressor.setSpeed(dto.getSpeed());
        compressor.setHours(dto.getHours());

        return compressor;
    }

    private HeatingData mapHeatingData(HeatPumpDataResponse.HeatingDto dto) {
        if (dto == null) return null;

        HeatingData heating = new HeatingData();
        heating.setSetpoint(toBigDecimal(dto.getSetpoint()));
        heating.setHours(dto.getHours());
        heating.setExternalHeaterHours(dto.getExternalHeaterHours());

        return heating;
    }

    private HeatCurveData mapHeatCurveData(HeatPumpDataResponse.HeatCurveDto dto) {
        if (dto == null) return null;

        HeatCurveData heatCurve = new HeatCurveData();

        if (dto.getOutdoorTemp() != null) {
            heatCurve.setOutdoorTemps(dto.getOutdoorTemp().stream()
                    .map(this::toBigDecimal)
                    .collect(Collectors.toList()));
        }

        if (dto.getSupplyTemp() != null) {
            heatCurve.setSupplyTemps(dto.getSupplyTemp().stream()
                    .map(this::toBigDecimal)
                    .collect(Collectors.toList()));
        }

        return heatCurve;
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }
}
