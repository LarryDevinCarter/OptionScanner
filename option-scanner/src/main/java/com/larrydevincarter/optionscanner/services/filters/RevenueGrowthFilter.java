package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class RevenueGrowthFilter implements FinancialFilter{

    private double cagrThreshold;
    private int years;

    @Override
    public boolean appliesTo(String symbol, List<IncomeStatement> statements) {

        List<IncomeStatement> sortedStatements = statements.stream()
                .filter(s -> "annual".equals(s.getReportType()))
                .filter(s -> s.getTotalRevenue() !=  null)
                .sorted((s1, s2) -> s2.getFiscalDateEnding().compareTo(s1.getFiscalDateEnding()))
                .limit(years)
                .toList();

        if (sortedStatements.size() < years) {
            if (!sortedStatements.isEmpty()) {
                log.debug("Insufficient data for symbol {}: only {} years available", symbol, sortedStatements.size());
            } else {
                log.debug("No data for symbol {}", symbol);
            }
            return false;
        }

        double endingRevenue = sortedStatements.get(0).getTotalRevenue();
        double beginningRevenue = sortedStatements.get(sortedStatements.size() -1).getTotalRevenue();

        if (beginningRevenue <= 0) {
            log.debug("Invalid beginning revenue for symbol {}: {}", symbol, beginningRevenue);
            return false;
        }
        double cagr = (Math.pow(endingRevenue/beginningRevenue, 1.0 / years) -1) * 100;
        log.debug("CAGR for symbol {}: {}%", symbol, cagr);
        return cagr > cagrThreshold;
    }

    @Override
    public String getName() {
        return "Revenue Growth";
    }
}
