package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class OperatingMarginFilter implements FinancialFilter<IncomeStatement> {

    private double marginThreshold;
    private int years;

    @Override
    public boolean appliesTo(String symbol, List<IncomeStatement> statements) {
        double averageMargin = calculateAverageMargin(symbol, statements);
        if (averageMargin < 0) {
            return false;
        }
        log.debug("Average Operating Margin for symbol {} over {} years: {}% (threshold: {}%)",
                symbol, years, averageMargin, marginThreshold);
        return averageMargin > marginThreshold;
    }

    public double calculateAverageMargin(String symbol, List<IncomeStatement> statements) {
        List<IncomeStatement> sortedStatements = statements.stream()
                .filter(s -> "annual".equals(s.getReportType()))
                .filter(s -> s.getOperatingIncome() != null && s.getTotalRevenue() != null)
                .sorted((s1, s2) -> s2.getFiscalDateEnding().compareTo(s1.getFiscalDateEnding()))
                .limit(years)
                .toList();

        if (sortedStatements.size() < years) {
            return -1.0;
        }

        double totalMargin = 0.0;
        for (IncomeStatement statement : sortedStatements) {
            if (statement.getTotalRevenue() <= 0) {
                return -1.0;
            }
            double margin = (statement.getOperatingIncome() / statement.getTotalRevenue()) * 100;
            totalMargin += margin;
        }

        return totalMargin / years;
    }

    @Override
    public String getName() {
        return "Operating Margin";
    }
}