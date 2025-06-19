package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "balance_sheets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "fiscal_date_ending", "report_type"}))
@Data
public class BalanceSheet {

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

    @Column(name = "total_assets")
    private Double totalAssets;

    @Column(name = "total_current_assets")
    private Double totalCurrentAssets;

    @Column(name = "cash_and_cash_equivalents_at_carrying_value")
    private Double cashAndCashEquivalentsAtCarryingValue;

    @Column(name = "cash_and_short_term_investments")
    private Double cashAndShortTermInvestments;

    @Column(name = "inventory")
    private Double inventory;

    @Column(name = "current_net_receivables")
    private Double currentNetReceivables;

    @Column(name = "total_non_current_assets")
    private Double totalNonCurrentAssets;

    @Column(name = "intangible_assets")
    private Double intangibleAssets;

    @Column(name = "intangible_assets_excluding_goodwill")
    private Double intangibleAssetsExcludingGoodwill;

    @Column(name = "goodwill")
    private Double goodwill;

    @Column(name = "total_liabilities")
    private Double totalLiabilities;

    @Column(name = "total_current_liabilities")
    private Double totalCurrentLiabilities;

    @Column(name = "current_accounts_payable")
    private Double currentAccountsPayable;

    @Column(name = "short_term_debt")
    private Double shortTermDebt;

    @Column(name = "total_non_current_liabilities")
    private Double totalNonCurrentLiabilities;

    @Column(name = "capital_lease_obligations")
    private Double capitalLeaseObligations;

    @Column(name = "long_term_debt")
    private Double longTermDebt;

    @Column(name = "current_long_term_debt")
    private Double currentLongTermDebt;

    @Column(name = "short_long_term_debt_total")
    private Double shortLongTermDebtTotal;

    @Column(name = "other_current_liabilities")
    private Double otherCurrentLiabilities;

    @Column(name = "other_non_current_liabilities")
    private Double otherNonCurrentLiabilities;

    @Column(name = "total_shareholder_equity")
    private Double totalShareholderEquity;

    @Column(name = "retained_earnings")
    private Double retainedEarnings;

    @Column(name = "common_stock")
    private Double commonStock;

    @Column(name = "common_stock_shares_outstanding")
    private Double commonStockSharesOutstanding;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
    private Asset asset;
}