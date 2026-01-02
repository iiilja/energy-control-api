package com.ilja.smarthome.energycontrol.repository;

import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NordpoolPriceRepository extends JpaRepository<NordpoolPrice, Long> {

    Optional<NordpoolPrice> findByPriceTimestamp(ZonedDateTime timestamp);

    List<NordpoolPrice> findByPriceTimestampBetweenOrderByPriceTimestampAsc(
            ZonedDateTime start,
            ZonedDateTime end
    );

    Optional<NordpoolPrice> findFirstByOrderByPriceTimestampDesc();

    @Query("SELECT p FROM NordpoolPrice p WHERE p.priceTimestamp <= :timestamp " +
           "ORDER BY p.priceTimestamp DESC LIMIT 1")
    Optional<NordpoolPrice> findPriceAtTimestamp(@Param("timestamp") ZonedDateTime timestamp);

    void deleteByPriceTimestampBefore(ZonedDateTime before);

    long countByPriceTimestampBetween(ZonedDateTime start, ZonedDateTime end);
}
