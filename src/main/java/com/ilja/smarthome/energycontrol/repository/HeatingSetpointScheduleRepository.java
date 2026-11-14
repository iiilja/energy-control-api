package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface HeatingSetpointScheduleRepository extends JpaRepository<HeatingSetpointSchedule, Long> {

    Optional<HeatingSetpointSchedule> findByNordpoolPrice(NordpoolPrice price);

    /** Schedules whose price slot lies in the half-open period [start, end), with their price loaded. */
    @Query("SELECT s FROM HeatingSetpointSchedule s " +
           "JOIN FETCH s.nordpoolPrice p " +
           "WHERE p.priceTimestamp >= :start AND p.priceTimestamp < :end")
    List<HeatingSetpointSchedule> findInPeriod(
            @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end
    );

    void deleteByNordpoolPrice(NordpoolPrice price);

    /**
     * Deletes the given rows, but only those that still have {@code source}: a row turned into a manual
     * override in the meantime is kept. Runs at once (bulk delete).
     */
    @Modifying
    @Query("DELETE FROM HeatingSetpointSchedule s WHERE s.id IN :ids AND s.source = :source")
    int deleteByIdInAndSource(@Param("ids") Collection<Long> ids,
                              @Param("source") HeatingSetpointSchedule.Source source);
}
