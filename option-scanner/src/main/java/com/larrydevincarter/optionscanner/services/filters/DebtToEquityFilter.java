package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.BalanceSheet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.larrydevincarter.optionscanner.utils.FinancialFilterUtils.ANNUAL_REPORT_TYPE;

/**
 * A filter that evaluates companies based on their debt-to-equity ratio.
 */
@Slf4j
@Data
@AllArgsConstructor
public class DebtToEquityFilter implements FinancialFilter {

    private double debtToEquityThreshold;

    /**
     * Checks if the company's debt-to-equity ratio is below the specified threshold.
     *
     * @param symbol  the stock symbol
     * @param reports the financial reports containing balance sheets
     * @return true if the ratio is below the threshold, false otherwise
     */
    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double ratio = calculateRatio(symbol, reports.getBalanceSheets());
        if (ratio < 0) {
            return false;
        }
        log.debug("Debt-to-Equity Ratio for symbol {}: {} (threshold: {})", symbol, ratio, debtToEquityThreshold);
        return ratio < debtToEquityThreshold;
    }

    /**
     * Calculates the debt-to-equity ratio for the most recent annual balance sheet.
     *
     * @param symbol        the stock symbol
     * @param balanceSheets the list of balance sheets
     * @return the debt-to-equity ratio, or INVALID_RESULT if calculation is not possible
     */
    public double calculateRatio(String symbol, List<BalanceSheet> balanceSheets) {
        return balanceSheets.stream()
                .filter(s -> ANNUAL_REPORT_TYPE.equals(s.getReportType()))
                .filter(s -> s.getTotalLiabilities() != null && s.getTotalShareholderEquity() != null)
                .max((s1, s2) -> s1.getFiscalDateEnding().compareTo(s2.getFiscalDateEnding()))
                .map(bs -> {
                    double totalEquity = bs.getTotalShareholderEquity();
                    return totalEquity > 0 ? bs.getTotalLiabilities() / totalEquity : -1.0;
                })
                .orElse(INVALID_RESULT);
    }

    /**
     * Returns the name of the filter.
     *
     * @return the filter name, "Debt-to-Equity Ratio"
     */
    @Override
    public String getName() {
        return "Debt-to-Equity Ratio";
    }
}