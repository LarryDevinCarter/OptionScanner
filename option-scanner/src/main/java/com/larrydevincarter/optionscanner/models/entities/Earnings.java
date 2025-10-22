package com.larrydevincarter.optionscanner.models.entities;

import com.larrydevincarter.optionscanner.models.entities.base.BaseFinancialReport;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * Entity representing a company's earnings data, capturing earnings per share (EPS) and related metrics
 * for a fiscal period. Used to evaluate stock performance, sourced from Alpha Vantage.
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "earnings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "fiscal_date_ending", "report_type"}))
@Data
public class Earnings extends BaseFinancialReport {

    @NotNull
    @Column(name = "fiscal_date_ending")
    private LocalDate fiscalDateEnding;

    @NotNull
    @Column(name = "report_type")
    private String reportType;

    @Column(name = "reported_eps")
    private Double reportedEPS;

    // The next 4 fields are for quarterly reports only
    @Column(name = "reported_date")
    private LocalDate reportedDate;

    @Column(name = "estimated_eps")
    private Double estimatedEPS;

    @Column(name = "surprise")
    private Double surprise;

    @Column(name = "surprise_percentage")
    private Double surprisePercentage;
}