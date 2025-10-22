package com.larrydevincarter.optionscanner.models.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "options",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol"}))
public class Option {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "underlying_symbol", nullable = false)
    private String underlyingSymbol;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(name = "strike", nullable = false)
    private Double strike;

    @Column(name = "option_type", nullable = false)
    private String optionType;

    @Column(name = "previous_close")
    private Double previousClose;

    @Column(name = "traded_previous_day")
    private boolean tradedPreviousDay;

    @Column(name = "adjusted_pe")
    private Double adjustedPe;

    @Column(name = "yield")
    private Double yield;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "underlying_symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
    private Asset asset;
}