package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.*;
import com.larrydevincarter.optionscanner.repositories.*;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.filters.*;
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
    private final CashFlowRepository cashFlowRepository;

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

    @Value("${debt.to.equity.threshold}")
    private double defaultDebtToEquityThreshold;

    @Value("${fcf.yield.threshold:4.0}")
    private double defaultFcfYieldThreshold;

    @Override
    public List<String> getSymbolsWithRevenueGrowth(double cagrThreshold, int years) {

        FinancialFilter<IncomeStatement> revenueFilter = new RevenueGrowthFilter(
                cagrThreshold >= 0 ? cagrThreshold : defaultCagrThreshold,
                years > 0 ? years : defaultYears
        );

        return getFilteredSymbols(List.of(revenueFilter));
    }

    @Override
    public List<String> getSymbolsWithEpsGrowth(double cagrThreshold, int years) {
        FinancialFilter<Earnings> epsFilter = new EpsGrowthFilter(
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
    public List<String> getSymbolsWithDebtToEquity(double debtToEquityThreshold) {
        FinancialFilter<BalanceSheet> debtToEquityFilter = new DebtToEquityFilter(
                debtToEquityThreshold >= 0 ? debtToEquityThreshold : defaultDebtToEquityThreshold
        );
        return getFilteredSymbols(List.of(debtToEquityFilter));
    }

    @Override
    public List<String> getSymbolsWithFcfYield(double fcfYieldThreshold) {
        FinancialFilter<Object> fcfYieldFilter = new FreeCashFlowYieldFilter(
                fcfYieldThreshold >= 0 ? fcfYieldThreshold : defaultFcfYieldThreshold
        );
        return getFilteredSymbols(List.of(fcfYieldFilter));
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

        List<CashFlow> cashFlows = cashFlowRepository.findAll();
        Map<String, List<CashFlow>> cashFlowBySymbol = cashFlows.stream()
                .collect(Collectors.groupingBy(CashFlow::getSymbol));

        List<Asset> assets = assetRepository.findAll();
        Map<String, List<Asset>> assetBySymbol = assets.stream()
                .collect(Collectors.groupingBy(Asset::getSymbol));

        List<String> filteredSymbols = activeTradableSymbols.stream().filter(symbol -> {

            List<IncomeStatement> symbolIncome = incomeBySymbol.getOrDefault(symbol, Collections.emptyList());
            List<Earnings> symbolEarnings = earningsBySymbol.getOrDefault(symbol, Collections.emptyList());
            List<BalanceSheet> symbolBalance = balanceBySymbol.getOrDefault(symbol, Collections.emptyList());
            List<CashFlow> symbolCashFlow = cashFlowBySymbol.getOrDefault(symbol, Collections.emptyList());
            List<Asset> symbolAsset = assetBySymbol.getOrDefault(symbol, Collections.emptyList());
            List<Object> combinedReports = new ArrayList<>();
            combinedReports.addAll(symbolIncome);
            combinedReports.addAll(symbolBalance);
            combinedReports.addAll(symbolCashFlow);
            combinedReports.addAll(symbolAsset);

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
                } else if (filter instanceof DebtToEquityFilter) {
                    @SuppressWarnings("unchecked")
                    FinancialFilter<BalanceSheet> debtToEquityFilter = (FinancialFilter<BalanceSheet>) filter;
                    return debtToEquityFilter.appliesTo(symbol, symbolBalance);
                } else if (filter instanceof FreeCashFlowYieldFilter) {
                    @SuppressWarnings("unchecked")
                    FinancialFilter<Object> fcfYieldFilter = (FinancialFilter<Object>) filter;
                    return fcfYieldFilter.appliesTo(symbol, combinedReports);
                }
                log.warn("Unknown filter type: {}. Skipping.", filter.getClass().getName());
                return true;
            });
        }).toList();

        log.info("Filtered to {} symbols with {} filters", filteredSymbols.size(), filters.size());
        return filteredSymbols;
    }
}
