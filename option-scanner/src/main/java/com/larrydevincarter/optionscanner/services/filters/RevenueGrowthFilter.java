package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.utils.FinancialFilterUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * A filter that evaluates companies based on their revenue growth, measured by Compound Annual Growth Rate (CAGR).
 */
@Slf4j
@Data
@AllArgsConstructor
public class RevenueGrowthFilter implements FinancialFilter{

    private double cagrThreshold;
    private int years;

    /**
     * Checks if the company's revenue CAGR meets the specified threshold.
     *
     * @param symbol  the stock symbol
     * @param reports the financial reports containing income statements
     * @return true if the CAGR exceeds the threshold, false otherwise
     */
    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double cagr = calculateCagr(reports.getIncomeStatements());
        if (cagr < 0) {
            return false;
        }
        log.debug("CAGR for symbol {}: {}%", symbol, cagr);
        return cagr > cagrThreshold;
    }

    /**
     * Calculates the revenue CAGR over the specified number of years.
     *
     * @param statements the list of income statements
     * @return the CAGR percentage, or INVALID_RESULT if calculation is not possible
     */
    public double calculateCagr(List<IncomeStatement> statements) {
        List<IncomeStatement> sortedStatements = FinancialFilterUtils.getAnnualReports(statements, years,
                s -> s.getTotalRevenue() != null,
                FinancialFilterUtils.INCOME_DATE_EXTRACTOR);

        if (sortedStatements.size() < years) {
            return INVALID_RESULT;
        }

        double endingRevenue = sortedStatements.getFirst().getTotalRevenue();
        double beginningRevenue = sortedStatements.getLast().getTotalRevenue();

        if (beginningRevenue <= 0) {
            return INVALID_RESULT;
        }
        return (Math.pow(endingRevenue / beginningRevenue, 1.0 / years) - 1) * 100;
    }

    /**
     * Returns the name of the filter.
     *
     * @return the filter name, "Revenue Growth"
     */
    @Override
    public String getName() {
        return "Revenue Growth";
    }
}
