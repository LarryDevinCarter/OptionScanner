package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.BalanceSheet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.larrydevincarter.optionscanner.utils.FinancialFilterUtils.ANNUAL_REPORT_TYPE;

@Slf4j
@Data
@AllArgsConstructor
public class DebtToEquityFilter implements FinancialFilter {

    private double debtToEquityThreshold;

    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double ratio = calculateRatio(symbol, reports.getBalanceSheets());
        if (ratio < 0) {
            return false;
        }
        log.debug("Debt-to-Equity Ratio for symbol {}: {} (threshold: {})", symbol, ratio, debtToEquityThreshold);
        return ratio < debtToEquityThreshold;
    }

    public double calculateRatio(String symbol, List<BalanceSheet> balanceSheets) {
        return balanceSheets.stream()
                .filter(s -> ANNUAL_REPORT_TYPE.equals(s.getReportType()))
                .filter(s -> s.getTotalLiabilities() != null && s.getTotalShareholderEquity() != null)
                .max((s1, s2) -> s1.getFiscalDateEnding().compareTo(s2.getFiscalDateEnding()))
                .map(bs -> {
                    double totalEquity = bs.getTotalShareholderEquity();
                    return totalEquity > 0 ? bs.getTotalLiabilities() / totalEquity : -1.0;
                })
                .orElse(-1.0);
    }

    @Override
    public String getName() {
        return "Debt-to-Equity Ratio";
    }
}