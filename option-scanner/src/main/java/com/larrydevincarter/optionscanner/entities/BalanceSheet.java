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
    private Long totalAssets;

    @Column(name = "total_current_assets")
    private Long totalCurrentAssets;

    @Column(name = "cash_and_cash_equivalents_at_carrying_value")
    private Long cashAndCashEquivalentsAtCarryingValue;

    @Column(name = "cash_and_short_term_investments")
    private Long cashAndShortTermInvestments;

    @Column(name = "inventory")
    private Long inventory;

    @Column(name = "current_net_receivables")
    private Long currentNetReceivables;

    @Column(name = "total_non_current_assets")
    private Long totalNonCurrentAssets;

    @Column(name = "intangible_assets")
    private Long intangibleAssets;

    @Column(name = "intangible_assets_excluding_goodwill")
    private Long intangibleAssetsExcludingGoodwill;

    @Column(name = "goodwill")
    private Long goodwill;

    @Column(name = "total_liabilities")
    private Long totalLiabilities;

    @Column(name = "total_current_liabilities")
    private Long totalCurrentLiabilities;

    @Column(name = "current_accounts_payable")
    private Long currentAccountsPayable;

    @Column(name = "short_term_debt")
    private Long shortTermDebt;

    @Column(name = "total_non_current_liabilities")
    private Long totalNonCurrentLiabilities;

    @Column(name = "capital_lease_obligations")
    private Long capitalLeaseObligations;

    @Column(name = "long_term_debt")
    private Long longTermDebt;

    @Column(name = "current_long_term_debt")
    private Long currentLongTermDebt;

    @Column(name = "short_long_term_debt_total")
    private Long shortLongTermDebtTotal;

    @Column(name = "other_current_liabilities")
    private Long otherCurrentLiabilities;

    @Column(name = "other_non_current_liabilities")
    private Long otherNonCurrentLiabilities;

    @Column(name = "total_shareholder_equity")
    private Long totalShareholderEquity;

    @Column(name = "retained_earnings")
    private Long retainedEarnings;

    @Column(name = "common_stock")
    private Long commonStock;

    @Column(name = "common_stock_shares_outstanding")
    private Long commonStockSharesOutstanding;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
    private Asset asset;
}