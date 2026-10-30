package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NordpoolPriceRepository extends JpaRepository<NordpoolPrice, Long> {

    Optional<NordpoolPrice> findByPriceTimestamp(ZonedDateTime timestamp);

    /** Prices in the half-open period [start, end): a day query must not include the next day's 00:00 slot. */
    @Query("SELECT p FROM NordpoolPrice p WHERE p.priceTimestamp >= :start AND p.priceTimestamp < :end " +
           "ORDER BY p.priceTimestamp ASC")
    List<NordpoolPrice> findInPeriod(@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    Optional<NordpoolPrice> findFirstByOrderByPriceTimestampDesc();

    @Query("SELECT p FROM NordpoolPrice p WHERE p.priceTimestamp <= :timestamp " +
           "ORDER BY p.priceTimestamp DESC LIMIT 1")
    Optional<NordpoolPrice> findPriceAtTimestamp(@Param("timestamp") ZonedDateTime timestamp);

    @Query("SELECT AVG(p.price) FROM NordpoolPrice p WHERE p.priceTimestamp >= :start AND p.priceTimestamp < :end")
    Optional<BigDecimal> findAveragePriceBetween(@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    void deleteByPriceTimestampBefore(ZonedDateTime before);

    /** Number of prices in the half-open period [start, end). */
    @Query("SELECT COUNT(p) FROM NordpoolPrice p WHERE p.priceTimestamp >= :start AND p.priceTimestamp < :end")
    long countInPeriod(@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);
}
