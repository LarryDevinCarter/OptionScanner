package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

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
