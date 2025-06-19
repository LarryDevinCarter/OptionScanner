package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;
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
public class EpsGrowthFilter implements FinancialFilter<Earnings> {

    private double cagrThreshold;
    private int years;

    @Override
    public boolean appliesTo(String symbol, List<Earnings> earnings) {

        List<Earnings> annualEarnings = earnings.stream()
                .filter(e -> "annual".equals(e.getReportType()))
                .filter(e -> e.getReportedEPS() != null)
                .sorted(Comparator.comparing(Earnings::getFiscalDateEnding).reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        if (annualEarnings.size() < 2) {
            log.debug("Insufficient earnings data for symbol {}: only {} annual records available", symbol, annualEarnings.size());
            return false;
        }
        Earnings mostRecent = annualEarnings.get(0);
        Earnings secondMostRecent = annualEarnings.get(1);
        int fiscalYearEndMonth = secondMostRecent.getFiscalDateEnding().getMonthValue();
        boolean isMostRecentPartial = mostRecent.getFiscalDateEnding().getMonthValue() != fiscalYearEndMonth;
        log.debug("Inferred fiscal year-end month for symbol {}: {}. Most recent record ({}): {}partial", symbol, fiscalYearEndMonth, mostRecent.getFiscalDateEnding(), isMostRecentPartial ? "" : "not ");

        if (isMostRecentPartial) {
            annualEarnings.removeFirst();
        }

        if (annualEarnings.size() <= years) {
            log.debug("Insufficient full-year earnings data for symbol {}: only {} years available for fiscal year-end month {}",
                    symbol, annualEarnings.size(), fiscalYearEndMonth);
            return false;
        }
        double endingEps = annualEarnings.getFirst().getReportedEPS();
        double beginningEps = annualEarnings.get(years).getReportedEPS();

        if (beginningEps <= 0) {
            log.debug("Invalid beginning EPS for symbol {}: {}", symbol, beginningEps);
            return false;
        }

        if (endingEps <= 0) {
            log.debug("Invalid ending EPS for symbol {}: {}", symbol, endingEps);
            return false;
        }
        double cagr = (Math.pow(endingEps / beginningEps, 1.0 / years) - 1) * 100;
        log.debug("EPS CAGR for symbol {}: {}% (threshold: {}%)", symbol, cagr, cagrThreshold);
        return cagr > cagrThreshold;
    }

    @Override
    public String getName() {
        return "EPS Growth";
    }
}