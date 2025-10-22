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
public class RevenueGrowthFilter implements FinancialFilter{

    private double cagrThreshold;
    private int years;

    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double cagr = calculateCagr(reports.getIncomeStatements());
        if (cagr < 0) {
            return false;
        }
        log.debug("CAGR for symbol {}: {}%", symbol, cagr);
        return cagr > cagrThreshold;
    }

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

    @Override
    public String getName() {
        return "Revenue Growth";
    }
}
