package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "assets")
public class Asset {

    @Id
    @Column(name = "id", nullable = false)
    private String id;
    @Column(name = "symbol", nullable = false, unique = true)
    private String symbol;
    @Column(name = "name")
    private String name;
    @Column(name = "exchange")
    private String exchange;
    @Column(name = "status")
    private String status;
    @Column(name = "tradable")
    private boolean tradable;
    @Column(name = "current_price")
    private Double currentPrice;
    @Column(name = "last_price_updated")
    private LocalDateTime lastPriceUpdated;
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    @Column(name = "adjusted_net_income")
    private Double adjustedNetIncome;
    @Column(name = "adjusted_earnings_per_share")
    private Double adjustedEarningsPerShare;
}
