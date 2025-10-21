package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.Earnings;
import com.larrydevincarter.optionscanner.utils.FinancialFilterUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Data
@AllArgsConstructor
public class EpsGrowthFilter implements FinancialFilter {

    private double cagrThreshold;
    private int years;

    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double cagr = calculateCagr(symbol, reports.getEarnings());
        if (cagr < 0) {
            return false;
        }
        log.debug("EPS CAGR for symbol {}: {}% (threshold: {}%)", symbol, cagr, cagrThreshold);
        return cagr > cagrThreshold;
    }

    public double calculateCagr(String symbol, List<Earnings> earnings) {
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

    @Override
    public String getName() {
        return "EPS Growth";
    }
}