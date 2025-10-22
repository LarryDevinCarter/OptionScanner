package com.larrydevincarter.optionscanner.models.entities;

import com.larrydevincarter.optionscanner.models.entities.base.BaseFinancialReport;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * Entity representing a dividend payment for a stock, capturing key dates and amount.
 * Used to track dividend-related data, sourced from Alpha Vantage.
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "dividends",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "ex_dividend_date"}))
@Data
public class Dividend extends BaseFinancialReport {

    @NotNull
    @Column(name = "ex_dividend_date")
    private LocalDate exDividendDate;

    @Column(name = "declaration_date")
    private LocalDate declarationDate;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @NotNull
    @Column(name = "amount")
    private Double amount;
}