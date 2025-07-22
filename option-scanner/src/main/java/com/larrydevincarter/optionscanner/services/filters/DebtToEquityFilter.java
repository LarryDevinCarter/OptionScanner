package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.BalanceSheet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class DebtToEquityFilter implements FinancialFilter<BalanceSheet> {

    private double debtToEquityThreshold;

    @Override
    public boolean appliesTo(String symbol, List<BalanceSheet> balanceSheets) {
        double ratio = calculateRatio(symbol, balanceSheets);
        if (ratio < 0) {
            return false;
        }
        log.debug("Debt-to-Equity Ratio for symbol {}: {} (threshold: {})", symbol, ratio, debtToEquityThreshold);
        return ratio < debtToEquityThreshold;
    }

    public double calculateRatio(String symbol, List<BalanceSheet> balanceSheets) {
        BalanceSheet latestBalanceSheet = balanceSheets.stream()
                .filter(s -> "annual".equals(s.getReportType()))
                .filter(s -> s.getTotalLiabilities() != null && s.getTotalShareholderEquity() != null)
                .max((s1, s2) -> s1.getFiscalDateEnding().compareTo(s2.getFiscalDateEnding()))
                .orElse(null);

        if (latestBalanceSheet == null) {
            return -1.0;
        }

        double totalLiabilities = latestBalanceSheet.getTotalLiabilities();
        double totalEquity = latestBalanceSheet.getTotalShareholderEquity();

        if (totalEquity <= 0) {
            return -1.0;
        }

        return totalLiabilities / totalEquity;
    }

    @Override
    public String getName() {
        return "Debt-to-Equity Ratio";
    }
}