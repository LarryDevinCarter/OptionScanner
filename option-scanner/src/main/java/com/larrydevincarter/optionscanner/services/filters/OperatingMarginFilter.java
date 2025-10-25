package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.utils.FinancialFilterUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * A filter that evaluates companies based on their average operating margin over a specified period.
 */
@Slf4j
@Data
@AllArgsConstructor
public class OperatingMarginFilter implements FinancialFilter {

    private double marginThreshold;
    private int years;

    /**
     * Checks if the company's average operating margin meets the specified threshold.
     *
     * @param symbol  the stock symbol
     * @param reports the financial reports containing income statements
     * @return true if the average margin exceeds the threshold, false otherwise
     */
    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double averageMargin = calculateAverageMargin(reports.getIncomeStatements());
        if (averageMargin < 0) {
            return false;
        }
        log.debug("Average Operating Margin for symbol {} over {} years: {}% (threshold: {}%)",
                symbol, years, averageMargin, marginThreshold);
        return averageMargin > marginThreshold;
    }

    /**
     * Calculates the average operating margin over the specified number of years.
     *
     * @param statements the list of income statements
     * @return the average operating margin percentage, or INVALID_RESULT if calculation is not possible
     */
    public double calculateAverageMargin(List<IncomeStatement> statements) {
        List<IncomeStatement> sortedStatements = FinancialFilterUtils.getAnnualReports(statements, years,
                s -> s.getOperatingIncome() != null && s.getTotalRevenue() != null,
                FinancialFilterUtils.INCOME_DATE_EXTRACTOR);

        if (sortedStatements.size() < years) {
            return INVALID_RESULT;
        }

        double totalMargin = 0.0;
        for (IncomeStatement statement : sortedStatements) {
            if (statement.getTotalRevenue() <= 0) {
                return INVALID_RESULT;
            }
            double margin = (statement.getOperatingIncome() / statement.getTotalRevenue()) * 100;
            totalMargin += margin;
        }

        return totalMargin / years;
    }

    /**
     * Returns the name of the filter.
     *
     * @return the filter name, "Operating Margin"
     */
    @Override
    public String getName() {
        return "Operating Margin";
    }
}