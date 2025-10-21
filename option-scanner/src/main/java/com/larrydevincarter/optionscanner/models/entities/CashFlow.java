package com.larrydevincarter.optionscanner.models.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_flows",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "fiscal_date_ending", "report_type"}))
@Data
public class CashFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "fiscal_date_ending", nullable = false)
    private LocalDate fiscalDateEnding;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "reported_currency")
    private String reportedCurrency;

    @Column(name = "operating_cashflow")
    private Double operatingCashflow;

    @Column(name = "capital_expenditures")
    private Double capitalExpenditures;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
    private Asset asset;
}