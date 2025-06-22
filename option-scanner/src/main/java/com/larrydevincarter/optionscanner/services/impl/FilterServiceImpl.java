package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.BalanceSheetRepository;
import com.larrydevincarter.optionscanner.repositories.EarningsRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.filters.EpsGrowthFilter;
import com.larrydevincarter.optionscanner.services.filters.FinancialFilter;
import com.larrydevincarter.optionscanner.services.filters.RevenueGrowthFilter;
import com.larrydevincarter.optionscanner.services.filters.RoicFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private final BalanceSheetRepository balanceSheetRepository;

    @Value("${revenue.growth.cagr.threshold}")
    private double defaultCagrThreshold;
    @Value("${revenue.growth.years}")
    private int defaultYears;

    @Value("${eps.growth.cagr.threshold}")
    private double defaultEpsCagrThreshold;
    @Value("${eps.growth.years}")
    private int defaultEpsYears;


    @Value("${roic.threshold}")
    private double defaultRoicThreshold;
    @Value("${roic.years}")
    private int defaultRoicYears;
    @Value("${roic.default.tax.rate}")
    private double defaultTaxRate;

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
    public List<String> getSymbolsWithRoic(double roicThreshold, int years, double defaultTaxRate) {
        FinancialFilter<Object> roicFilter = new RoicFilter(
                roicThreshold >= 0 ? roicThreshold : defaultRoicThreshold,
                years > 0 ? years : defaultRoicYears,
                defaultTaxRate >= 0 ? defaultTaxRate : this.defaultTaxRate
        );
        return getFilteredSymbols(List.of(roicFilter));
    }

    @Override
    public List<String> getFilteredSymbols(List<FinancialFilter<?>> filters) {

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

        List<BalanceSheet> balanceSheets = balanceSheetRepository.findAll();
        Map<String, List<BalanceSheet>> balanceBySymbol = balanceSheets.stream()
                .collect(Collectors.groupingBy(BalanceSheet::getSymbol));

        List<String> filteredSymbols = activeTradableSymbols.stream().filter(symbol -> {

            List<IncomeStatement> symbolIncome = incomeBySymbol.getOrDefault(symbol, Collections.emptyList());
            List<Earnings> symbolEarnings = earningsBySymbol.getOrDefault(symbol, Collections.emptyList());
            List<BalanceSheet> symbolBalance = balanceBySymbol.getOrDefault(symbol, Collections.emptyList());
            List<Object> combinedReports = new ArrayList<>();
            combinedReports.addAll(symbolIncome);
            combinedReports.addAll(symbolBalance);

            return filters.stream().allMatch(filter -> {

                if (filter instanceof RevenueGrowthFilter) {
                    @SuppressWarnings("unchecked")
                    FinancialFilter<IncomeStatement> incomeFilter = (FinancialFilter<IncomeStatement>) filter;
                    return incomeFilter.appliesTo(symbol, symbolIncome);
                } else if (filter instanceof EpsGrowthFilter) {
                    @SuppressWarnings("unchecked")
                    FinancialFilter<Earnings> incomeFilter = (FinancialFilter<Earnings>) filter;
                    return incomeFilter.appliesTo(symbol, symbolEarnings);
                } else if (filter instanceof RoicFilter) {
                    @SuppressWarnings("unchecked")
                    FinancialFilter<Object> roicFilter = (FinancialFilter<Object>) filter;
                    return roicFilter.appliesTo(symbol, combinedReports);
                }
                log.warn("Unknown filter type: {}. Skipping.", filter.getClass().getName());
                return true;
            });
        }).toList();

        log.info("Filtered to {} symbols with {} filters", filteredSymbols.size(), filters.size());
        return filteredSymbols;
    }
}
