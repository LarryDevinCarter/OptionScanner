package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class RevenueGrowthFilter implements FinancialFilter<IncomeStatement>{

    private double cagrThreshold;
    private int years;

    @Override
    public boolean appliesTo(String symbol, List<IncomeStatement> statements) {
        double cagr = calculateCagr(statements);
        if (cagr < 0) {
            return false;
        }
        log.debug("CAGR for symbol {}: {}%", symbol, cagr);
        return cagr > cagrThreshold;
    }

    public double calculateCagr(List<IncomeStatement> statements) {
        List<IncomeStatement> sortedStatements = statements.stream()
                .filter(s -> "annual".equals(s.getReportType()))
                .filter(s -> s.getTotalRevenue() != null)
                .sorted((s1, s2) -> s2.getFiscalDateEnding().compareTo(s1.getFiscalDateEnding()))
                .limit(years)
                .toList();

        if (sortedStatements.size() < years) {
            return -1.0;
        }

        double endingRevenue = sortedStatements.getFirst().getTotalRevenue();
        double beginningRevenue = sortedStatements.getLast().getTotalRevenue();

        if (beginningRevenue <= 0) {
            return -1.0;
        }
        return (Math.pow(endingRevenue / beginningRevenue, 1.0 / years) - 1) * 100;
    }

    @Override
    public String getName() {
        return "Revenue Growth";
    }
}
