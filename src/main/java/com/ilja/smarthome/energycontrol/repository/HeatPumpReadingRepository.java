package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.HeatPumpReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for HeatPumpReading entities.
 * Provides data access methods for heat pump readings.
 */
@Repository
public interface HeatPumpReadingRepository extends JpaRepository<HeatPumpReading, Long> {

    /**
     * Find the most recent reading.
     */
    Optional<HeatPumpReading> findFirstByOrderByCollectionTimestampDesc();

    /**
     * Find readings within a date range, ordered by collection timestamp descending.
     */
    Page<HeatPumpReading> findByCollectionTimestampBetweenOrderByCollectionTimestampDesc(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    /**
     * Count readings within a date range.
     */
    long countByCollectionTimestampBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find readings where outdoor temperature is below a threshold.
     */
    @Query("SELECT r FROM HeatPumpReading r WHERE r.temperatures.outdoorTemp < :threshold " +
           "ORDER BY r.collectionTimestamp DESC")
    Page<HeatPumpReading> findByOutdoorTempBelowThreshold(
            @Param("threshold") java.math.BigDecimal threshold,
            Pageable pageable
    );
}
