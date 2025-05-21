package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.EarningsRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.filters.EpsGrowthFilter;
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
    private final EarningsRepository earningsRepository;

    @Value("${revenue.growth.cagr.threshold}")
    private double defaultCagrThreshold;
    @Value("${revenue.growth.years}")
    private int defaultYears;

    @Value("${eps.growth.cagr.threshold}")
    private double defaultEpsCagrThreshold;
    @Value("${eps.growth.years}")
    private int defaultEpsYears;

    @Override
    public List<String> getSymbolsWithRevenueGrowth(double cagrThreshold, int years) {

        FinancialFilter revenueFilter = new RevenueGrowthFilter(
                cagrThreshold >= 0 ? cagrThreshold : defaultCagrThreshold,
                years > 0 ? years : defaultYears
        );

        return getFilteredSymbols(List.of(revenueFilter));
    }

    @Override
    public List<String> getSymbolsWithEpsGrowth(double cagrThreshold, int years) {
        FinancialFilter epsFilter = new EpsGrowthFilter(
                cagrThreshold >= 0 ? cagrThreshold : defaultEpsCagrThreshold,
                years > 0 ? years : defaultEpsYears
        );
        return getFilteredSymbols(List.of(epsFilter));
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
        Map<String, List<IncomeStatement>> incomeBySymbol = statements.stream().collect(Collectors.groupingBy(IncomeStatement::getSymbol));

        List<Earnings> earnings = earningsRepository.findAll();
        Map<String, List<Earnings>> earningsBySymbol = earnings.stream().collect(Collectors.groupingBy(Earnings::getSymbol));

        List<String> filteredSymbols = activeTradableSymbols.stream().filter(symbol -> {
            List<IncomeStatement> symbolIncome = incomeBySymbol.getOrDefault(symbol, Collections.emptyList());
            List<Earnings> symbolEarnings = earningsBySymbol.getOrDefault(symbol, Collections.emptyList());
            return filters.stream().allMatch(filter -> filter.appliesToIncome(symbol, symbolIncome) && filter.appliesToEarnings(symbol, symbolEarnings));
        }).toList();

        log.info("Filtered to {} symbols with {} filters", filteredSymbols.size(), filters.size());
        return filteredSymbols;
    }
}
