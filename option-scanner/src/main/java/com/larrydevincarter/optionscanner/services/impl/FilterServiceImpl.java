package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.dtos.StockCandidatesRequestDto;
import com.larrydevincarter.optionscanner.models.entities.*;
import com.larrydevincarter.optionscanner.repositories.*;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.filters.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Data
public class FilterServiceImpl implements FilterService {

    private final AssetRepository assetRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final EarningsRepository earningsRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;

    @Value("${revenue.growth.cagr.threshold}")
    private double defaultCagrThreshold;
    @Value("${revenue.growth.years}")
    private int revenueYears;

    @Value("${eps.growth.cagr.threshold}")
    private double epsCagrThreshold;
    @Value("${eps.growth.years}")
    private int epsYears;


    @Value("${roic.threshold}")
    private double roicThreshold;
    @Value("${roic.years}")
    private int roicYears;
    @Value("${roic.default.tax.rate}")
    private double defaultTaxRate;

    @Value("${debt.to.equity.threshold}")
    private double debtToEquityThreshold;

    @Value("${fcf.yield.threshold:4.0}")
    private double fcfYieldThreshold;

    @Value("${operating.margin.threshold}")
    private double operatingMarginThreshold;
    @Value("${operating.margin.years}")
    private int operatingMarginYears;

    private int currentHoldStreak = 0;
    private double currentRemainingLiquidity = 0.0;

    @Override
    public List<String> getSymbolsWithRevenueGrowth(double cagrThreshold, int years) {

        FinancialFilter revenueFilter = new RevenueGrowthFilter(
                cagrThreshold >= 0 ? cagrThreshold : defaultCagrThreshold,
                years > 0 ? years : revenueYears
        );

        return getFilteredSymbols(List.of(revenueFilter));
    }

    @Override
    public List<String> getSymbolsWithEpsGrowth(double cagrThreshold, int years) {
        FinancialFilter epsFilter = new EpsGrowthFilter(
                cagrThreshold >= 0 ? cagrThreshold : epsCagrThreshold,
                years > 0 ? years : epsYears
        );
        return getFilteredSymbols(List.of(epsFilter));
    }

    @Override
    public List<String> getSymbolsWithRoic(double roicThreshold, int years, double defaultTaxRate) {
        FinancialFilter roicFilter = new RoicFilter(
                roicThreshold >= 0 ? roicThreshold : this.roicThreshold,
                years > 0 ? years : roicYears,
                defaultTaxRate >= 0 ? defaultTaxRate : this.defaultTaxRate
        );
        return getFilteredSymbols(List.of(roicFilter));
    }

    @Override
    public List<String> getSymbolsWithDebtToEquity(double debtToEquityThreshold) {
        FinancialFilter debtToEquityFilter = new DebtToEquityFilter(
                debtToEquityThreshold >= 0 ? debtToEquityThreshold : this.debtToEquityThreshold
        );
        return getFilteredSymbols(List.of(debtToEquityFilter));
    }

    @Override
    public List<String> getSymbolsWithFcfYield(double fcfYieldThreshold) {
        FinancialFilter fcfYieldFilter = new FreeCashFlowYieldFilter(
                fcfYieldThreshold >= 0 ? fcfYieldThreshold : this.fcfYieldThreshold
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
        log.info("Found {} active tradable symbols", activeTradableSymbols.size());

        Map<String, List<IncomeStatement>> incomeBySymbol = incomeStatementRepository.findAll().stream()
                .collect(Collectors.groupingBy(IncomeStatement::getSymbol));

        Map<String, List<Earnings>> earningsBySymbol = earningsRepository.findAll().stream()
                .collect(Collectors.groupingBy(Earnings::getSymbol));

        Map<String, List<BalanceSheet>> balanceBySymbol = balanceSheetRepository.findAll().stream()
                .collect(Collectors.groupingBy(BalanceSheet::getSymbol));

        Map<String, List<CashFlow>> cashFlowBySymbol = cashFlowRepository.findAll().stream()
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

    @Override
    public List<String> getStockCandidates(StockCandidatesRequestDto dto) {
        log.info("dto = " + dto);
        this.currentHoldStreak = dto.getHoldStreak();
        this.currentRemainingLiquidity = dto.getRemainingLiquidity();
        double adjustment = 0.1 * currentHoldStreak;
        double debtAdjustment = 0.01 * currentHoldStreak;
        double mcAdjustment = 0.1 * currentHoldStreak;

        List<FinancialFilter> filters = new ArrayList<>();
        filters.add(new RevenueGrowthFilter(defaultCagrThreshold - adjustment, revenueYears));
        filters.add(new EpsGrowthFilter(epsCagrThreshold - adjustment, epsYears));
        filters.add(new RoicFilter(roicThreshold - adjustment, roicYears, defaultTaxRate));
        filters.add(new DebtToEquityFilter(debtToEquityThreshold + debtAdjustment));
        filters.add(new FreeCashFlowYieldFilter(fcfYieldThreshold - adjustment));
        filters.add(new OperatingMarginFilter(operatingMarginThreshold - adjustment, operatingMarginYears));

        double mcThresholdBillions = 5.0 - mcAdjustment;
        long minMarketCap = (long) (mcThresholdBillions * 1_000_000_000L);
        filters.add(new MarketCapFilter(minMarketCap));

        List<String> filteredSymbols = getFilteredSymbols(filters);

        Set<String> excluded = new HashSet<>(dto.getExcludedTickers());
        log.info("excluded = " + excluded);
        filteredSymbols = filteredSymbols.stream()
                .filter(s -> !excluded.contains(s))
                .collect(Collectors.toList());
        log.info("filteredSymbols = " + filteredSymbols);

        double maxPrice = dto.getRemainingLiquidity() / 100.0;

        List<Asset> assets = assetRepository.findBySymbols(filteredSymbols);
        log.info("assets = " + assets);
        Map<String, Double> pricesBySymbol = assets.stream()
                .collect(Collectors.toMap(Asset::getSymbol, Asset::getCurrentPrice));

        return filteredSymbols.stream()
                .filter(s -> {
                    Double price = pricesBySymbol.get(s);
                    return price != null && price <= maxPrice;
                })
                .sorted()
                .collect(Collectors.toList());
    }
}
