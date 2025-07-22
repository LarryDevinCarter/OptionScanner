package com.larrydevincarter.optionscanner.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "cash_flows",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "fiscal_date_ending", "report_type"}))
@Data
public class CashFlow extends BaseFinancialReport{

    @NotNull
    @Column(name = "fiscal_date_ending")
    private LocalDate fiscalDateEnding;

    @NotNull
    @Column(name = "report_type")
    private String reportType;

    @Column(name = "reported_currency")
    private String reportedCurrency;

    @Column(name = "operating_cashflow")
    private Double operatingCashflow;

    @Column(name = "capital_expenditures")
    private Double capitalExpenditures;
}