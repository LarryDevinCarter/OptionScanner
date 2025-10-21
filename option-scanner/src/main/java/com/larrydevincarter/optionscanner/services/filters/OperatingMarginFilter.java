package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.utils.FinancialFilterUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class OperatingMarginFilter implements FinancialFilter {

    private double marginThreshold;
    private int years;

    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double averageMargin = calculateAverageMargin(symbol, reports.getIncomeStatements());
        if (averageMargin < 0) {
            return false;
        }
        log.debug("Average Operating Margin for symbol {} over {} years: {}% (threshold: {}%)",
                symbol, years, averageMargin, marginThreshold);
        return averageMargin > marginThreshold;
    }

    public double calculateAverageMargin(String symbol, List<IncomeStatement> statements) {
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

    @Override
    public String getName() {
        return "Operating Margin";
    }
}