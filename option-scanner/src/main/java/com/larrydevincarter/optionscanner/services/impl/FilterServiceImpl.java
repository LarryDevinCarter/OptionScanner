package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.filters.FinancialFilter;
import com.larrydevincarter.optionscanner.services.filters.RevenueGrowthFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilterServiceImpl implements FilterService {

    private final AssetRepository assetRepository;
    private final IncomeStatementRepository incomeStatementRepository;

    @Value("${revenue.growth.cagr.threshold}")
    private double defaultCagrThreshold;
    @Value("${revenue.growth.years}")
    private int defaultYears;

    @Override
    public List<String> getSymbolsWithRevenueGrowth(double cagrThreshold, int years) {

        FinancialFilter revenueFilter = new RevenueGrowthFilter(
                cagrThreshold >= 0 ? cagrThreshold : defaultCagrThreshold,
                years > 0 ? years : defaultYears
        );

        return getFilteredSymbols(List.of(revenueFilter));
    }

    private boolean hasSufficientCagr(List<IncomeStatement> statements, double threshold, int years) {

        List<IncomeStatement> sortedStatements = statements.stream()
                .filter(s -> s.getTotalRevenue() != null)
                .sorted((s1, s2) -> s2.getFiscalDateEnding().compareTo(s1.getFiscalDateEnding()))
                .limit(years)
                .collect(Collectors.toList());

        if (sortedStatements.size() < years) {
            if (!sortedStatements.isEmpty()) {
                log.debug("Insufficient data for symbol {}: only {} years available", statements.get(0).getSymbol(), sortedStatements.size());
            } else {
                log.debug("No data.");
            }
            return false;
        }
        double endingRevenue = sortedStatements.get(0).getTotalRevenue();
        double beginningRevenue = sortedStatements.get(sortedStatements.size() - 1).getTotalRevenue();

        if (beginningRevenue <= 0) {
            log.debug("Invalid beginning revenue for symbol {}: {}", statements.get(0).getSymbol(), beginningRevenue);
            return false;
        }
        double cagr = (Math.pow(endingRevenue / beginningRevenue, 1.0 /years) - 1) * 100;
        log.debug("CAGR for symbol {}: {}%", statements.get(0).getSymbol(), cagr);
        return cagr > threshold;
    }

    @Override
    public List<String> getFilteredSymbols(List<FinancialFilter> filters) {

        List<String> activeTradableSymbols = assetRepository.findActiveTradableSymbols();
        log.info("Found {} active and tradable assets", activeTradableSymbols.size());

        if (filters == null || filters.isEmpty()) {
            log.info("No filters applied, returning all active/tradable symbols");
            return activeTradableSymbols;
        }
        List<IncomeStatement> statements = incomeStatementRepository.findAll();
        Map<String, List<IncomeStatement>> statementsBySymbol = statements.stream().collect(Collectors.groupingBy(IncomeStatement::getSymbol));

        List<String> filteredSymbols = activeTradableSymbols.stream().filter(symbol -> {
            List<IncomeStatement> symbolStatements = statementsBySymbol.getOrDefault(symbol, Collections.emptyList());
            return filters.stream().allMatch(filter -> filter.appliesTo(symbol, symbolStatements));
        }).toList();

        log.info("Filtered to {} symbols with {} filters", filteredSymbols.size(), filters.size());
        return filteredSymbols;
    }
}
