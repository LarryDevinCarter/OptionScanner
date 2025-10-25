package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;

/**
 * Interface for financial filters that evaluate companies based on financial metrics.
 */
public interface FinancialFilter {

    /**
     * Constant representing an invalid result for financial calculations.
     */
    double INVALID_RESULT = -1.0;

    /**
     * Determines if the company meets the filter's criteria.
     *
     * @param symbol  the stock symbol
     * @param reports the financial reports to evaluate
     * @return true if the company passes the filter, false otherwise
     */
    boolean appliesTo(String symbol, FinancialReports reports);

    /**
     * Returns the name of the filter.
     *
     * @return the filter name
     */
    String getName();
}
