package com.larrydevincarter.optionscanner.models.entities;

import com.larrydevincarter.optionscanner.models.entities.base.BaseFinancialReport;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * Entity representing a company's income statement, capturing revenue, expenses, and profit metrics
 * for a fiscal period. Used to analyze financial performance, sourced from Alpha Vantage.
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "income_statements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "fiscal_date_ending", "report_type"}))
@Data
public class IncomeStatement extends BaseFinancialReport implements HasFiscalDate{

    @NotNull
    @Column(name = "fiscal_date_ending")
    private LocalDate fiscalDateEnding;

    @NotNull
    @Column(name = "report_type")
    private String reportType;

    @Column(name = "reported_currency")
    private String reportedCurrency;

    @Column(name = "gross_profit")
    private Double grossProfit;

    @Column(name = "total_revenue")
    private Double totalRevenue;

    @Column(name = "cost_of_revenue")
    private Double costOfRevenue;

    @Column(name = "cost_of_goods_and_services_sold")
    private Double costOfGoodsAndServicesSold;

    @Column(name = "operating_income")
    private Double operatingIncome;

    @Column(name = "selling_general_and_administrative")
    private Double sellingGeneralAndAdministrative;

    @Column(name = "research_and_development")
    private Double researchAndDevelopment;

    @Column(name = "operating_expenses")
    private Double operatingExpenses;

    @Column(name = "investment_income_net")
    private Double investmentIncomeNet;

    @Column(name = "net_interest_income")
    private Double netInterestIncome;

    @Column(name = "interest_income")
    private Double interestIncome;

    @Column(name = "interest_expense")
    private Double interestExpense;

    @Column(name = "non_interest_income")
    private Double nonInterestIncome;

    @Column(name = "other_non_operating_income")
    private Double otherNonOperatingIncome;

    @Column(name = "depreciation")
    private Double depreciation;

    @Column(name = "depreciation_and_amortization")
    private Double depreciationAndAmortization;

    @Column(name = "income_before_tax")
    private Double incomeBeforeTax;

    @Column(name = "income_tax_expense")
    private Double incomeTaxExpense;

    @Column(name = "interest_and_debt_expense")
    private Double interestAndDebtExpense;

    @Column(name = "net_income_from_continuing_operations")
    private Double netIncomeFromContinuingOperations;

    @Column(name = "comprehensive_income_net_of_tax")
    private Double comprehensiveIncomeNetOfTax;

    @Column(name = "ebit")
    private Double ebit;

    @Column(name = "ebitda")
    private Double ebitda;

    @Column(name = "net_income")
    private Double netIncome;
}