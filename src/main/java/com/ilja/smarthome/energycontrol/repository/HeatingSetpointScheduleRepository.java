package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HeatingSetpointScheduleRepository extends JpaRepository<HeatingSetpointSchedule, Long> {

    Optional<HeatingSetpointSchedule> findByNordpoolPrice(NordpoolPrice price);

    List<HeatingSetpointSchedule> findByAppliedFalse();

    @Query("SELECT s FROM HeatingSetpointSchedule s " +
           "JOIN s.nordpoolPrice p " +
           "WHERE p.priceTimestamp <= :timestamp AND s.applied = false " +
           "ORDER BY p.priceTimestamp DESC LIMIT 1")
    Optional<HeatingSetpointSchedule> findNextPendingSchedule(@Param("timestamp") ZonedDateTime timestamp);

    @Query("SELECT s FROM HeatingSetpointSchedule s " +
           "JOIN s.nordpoolPrice p " +
           "WHERE p.priceTimestamp BETWEEN :start AND :end")
    List<HeatingSetpointSchedule> findByPriceTimestampBetween(
            @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end
    );

    void deleteByNordpoolPrice(NordpoolPrice price);
}
