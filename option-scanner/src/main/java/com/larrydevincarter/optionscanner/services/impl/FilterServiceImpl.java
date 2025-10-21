package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.*;
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

    @Value("${operating.margin.threshold}")
    private double operatingMarginThreshold;
    @Value("${operating.margin.years}")
    private int operatingMarginYears;

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
        FinancialFilter roicFilter = new RoicFilter(
                roicThreshold >= 0 ? roicThreshold : defaultRoicThreshold,
                years > 0 ? years : defaultRoicYears,
                defaultTaxRate >= 0 ? defaultTaxRate : this.defaultTaxRate
        );
        return getFilteredSymbols(List.of(roicFilter));
    }

    @Override
    public List<String> getSymbolsWithDebtToEquity(double debtToEquityThreshold) {
        FinancialFilter debtToEquityFilter = new DebtToEquityFilter(
                debtToEquityThreshold >= 0 ? debtToEquityThreshold : defaultDebtToEquityThreshold
        );
        return getFilteredSymbols(List.of(debtToEquityFilter));
    }

    @Override
    public List<String> getSymbolsWithFcfYield(double fcfYieldThreshold) {
        FinancialFilter fcfYieldFilter = new FreeCashFlowYieldFilter(
                fcfYieldThreshold >= 0 ? fcfYieldThreshold : defaultFcfYieldThreshold
        );
        return getFilteredSymbols(List.of(fcfYieldFilter));
    }

    @Override
    public List<String> getSymbolsWithOperatingMargin(double marginThreshold, int years) {
        FinancialFilter operatingMarginFilter = new OperatingMarginFilter(
                marginThreshold >= 0 ? marginThreshold : operatingMarginThreshold,  years >= 0 ? years : operatingMarginYears
        );
        return getFilteredSymbols(List.of(operatingMarginFilter));
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
            FinancialReports reports = new FinancialReports(symbolIncome, symbolEarnings, symbolBalance, symbolCashFlow, symbolAsset);

            return filters.stream().allMatch(filter -> filter.appliesTo(symbol, reports));
        }).toList();

        log.info("Filtered to {} symbols with {} filters", filteredSymbols.size(), filters.size());
        return filteredSymbols;
    }
}
