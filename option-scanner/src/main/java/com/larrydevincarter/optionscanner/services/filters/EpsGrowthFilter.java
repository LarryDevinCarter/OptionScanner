package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.Earnings;
import com.larrydevincarter.optionscanner.utils.FinancialFilterUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * A filter that evaluates companies based on their Earnings Per Share (EPS) growth, measured by Compound Annual Growth Rate (CAGR).
 */
@Slf4j
@Data
@AllArgsConstructor
public class EpsGrowthFilter implements FinancialFilter {

    private double cagrThreshold;
    private int years;

    /**
     * Checks if the company's EPS CAGR meets the specified threshold.
     *
     * @param symbol  the stock symbol
     * @param reports the financial reports containing earnings data
     * @return true if the EPS CAGR exceeds the threshold, false otherwise
     */
    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double cagr = calculateCagr(reports.getEarnings());
        if (cagr < 0) {
            return false;
        }
        log.debug("EPS CAGR for symbol {}: {}% (threshold: {}%)", symbol, cagr, cagrThreshold);
        return cagr > cagrThreshold;
    }

    /**
     * Calculates the EPS CAGR over the specified number of years.
     *
     * @param earnings the list of earnings data
     * @return the EPS CAGR percentage, or INVALID_RESULT if calculation is not possible
     */
    public double calculateCagr(List<Earnings> earnings) {
        List<Earnings> annualEarnings = FinancialFilterUtils.getAnnualReports(earnings, Integer.MAX_VALUE,
                e -> e.getReportedEPS() != null,
                FinancialFilterUtils.EARNINGS_DATE_EXTRACTOR);

        if (annualEarnings.size() < 2) {
            return INVALID_RESULT;
        }
        Earnings mostRecent = annualEarnings.get(0);
        Earnings secondMostRecent = annualEarnings.get(1);
        int fiscalYearEndMonth = secondMostRecent.getFiscalDateEnding().getMonthValue();
        boolean isMostRecentPartial = mostRecent.getFiscalDateEnding().getMonthValue() != fiscalYearEndMonth;

        if (isMostRecentPartial) {
            annualEarnings.removeFirst();
        }

        if (annualEarnings.size() <= years) {
            return INVALID_RESULT;
        }
        double endingEps = annualEarnings.getFirst().getReportedEPS();
        double beginningEps = annualEarnings.get(years).getReportedEPS();

        if (beginningEps <= 0 || endingEps <= 0) {
            return INVALID_RESULT;
        }
        return (Math.pow(endingEps / beginningEps, 1.0 / years) - 1) * 100;
    }

    /**
     * Returns the name of the filter.
     *
     * @return the filter name, "EPS Growth"
     */
    @Override
    public String getName() {
        return "EPS Growth";
    }
}