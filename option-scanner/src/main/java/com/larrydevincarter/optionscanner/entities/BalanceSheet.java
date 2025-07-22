package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "balance_sheets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "fiscal_date_ending", "report_type"}))
@Data
public class BalanceSheet extends BaseFinancialReport{

    @NotNull
    @Column(name = "fiscal_date_ending")
    private LocalDate fiscalDateEnding;

    @NotNull
    @Column(name = "report_type")
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
}