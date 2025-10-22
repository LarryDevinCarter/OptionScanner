package com.larrydevincarter.optionscanner.models.entities.base;

import com.larrydevincarter.optionscanner.models.entities.Asset;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Abstract base class for financial report entities, providing common fields for database persistence.
 * Represents shared attributes for financial data tied to a stock symbol, such as balance sheets,
 * income statements, cash flows, and earnings reports, sourced from Alpha Vantage API.
 */
@EqualsAndHashCode(exclude = "asset")
@MappedSuperclass
@Data
public abstract class BaseFinancialReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "symbol")
    private String symbol;

    @NotNull
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
    private Asset asset;
}
