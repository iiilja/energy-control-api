package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Entity representing Nordpool electricity price data.
 * Stores electricity prices for 15-minute intervals from Elering API.
 * Only Estonia (EE) region data is stored.
 */
@Entity
@Table(name = "nordpool_prices")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class NordpoolPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_timestamp", unique = true, nullable = false)
    private ZonedDateTime priceTimestamp;

    @Column(name = "price", nullable = false, precision = 10, scale = 4)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }

    public NordpoolPrice(ZonedDateTime priceTimestamp, BigDecimal price) {
        this.priceTimestamp = priceTimestamp;
        this.price = price;
    }
}
