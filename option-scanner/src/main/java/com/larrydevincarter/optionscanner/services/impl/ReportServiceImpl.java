package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.*;
import com.larrydevincarter.optionscanner.repositories.*;
import com.larrydevincarter.optionscanner.services.*;
import com.larrydevincarter.optionscanner.services.filters.*;
import com.larrydevincarter.optionscanner.utils.OptionSpecifications;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private FilterService filterService;

    @Autowired
    private IncomeStatementRepository incomeStatementRepository;

    @Autowired
    private EarningsRepository earningsRepository;

    @Autowired
    private BalanceSheetRepository balanceSheetRepository;

    @Autowired
    private CashFlowRepository cashFlowRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private OptionRepository optionRepository;

    @Value("${revenue.growth.cagr.threshold}")
    private double revenueCagrThreshold;
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

    @Value("${fcf.yield.threshold}")
    private double fcfYieldThreshold;

    @Value("${operating.margin.threshold}")
    private double operatingMarginThreshold;
    @Value("${operating.margin.years}")
    private int operatingMarginYears;

    private static final String BENCHMARK_SYMBOL = "TSLA";
    private static final double EPSILON = 0.01; // Small adjustment for floating-point comparisons
    private static final double[] YIELD_LEVELS = {0.23, 0.19, 0.16, 0.13, 0.09, 0.06};
    private static final List<Class<?>> FILTER_REMOVAL_ORDER = Arrays.asList(
            OperatingMarginFilter.class,
            FreeCashFlowYieldFilter.class,
            DebtToEquityFilter.class,
            RoicFilter.class,
            EpsGrowthFilter.class
    );

    public void generateReport(Double strikeMax) { // strikeMax is optional; pass null if not used
        // Step 1: Create filters and adjust thresholds based on TSLA
        RevenueGrowthFilter revenueFilter = new RevenueGrowthFilter(revenueCagrThreshold, revenueYears);
        EpsGrowthFilter epsFilter = new EpsGrowthFilter(epsCagrThreshold, epsYears);
        RoicFilter roicFilter = new RoicFilter(roicThreshold, roicYears, defaultTaxRate);
        DebtToEquityFilter debtFilter = new DebtToEquityFilter(debtToEquityThreshold);
        FreeCashFlowYieldFilter fcfFilter = new FreeCashFlowYieldFilter(fcfYieldThreshold);
        OperatingMarginFilter marginFilter = new OperatingMarginFilter(operatingMarginThreshold, operatingMarginYears);

        List<FinancialFilter<?>> allFilters = new ArrayList<>(List.of(
                revenueFilter, epsFilter, roicFilter, debtFilter, fcfFilter, marginFilter
        ));

        adjustFiltersForBenchmark(allFilters);

        // Step 2: Generate report
        List<String> reportLines = new ArrayList<>();
        Set<String> addedUnderlyings = new HashSet<>();
        Set<String> addedSymbol = new HashSet<>();
        int tier = 1;
        int yieldIndex = 0;
        List<FinancialFilter<?>> currentFilters = new ArrayList<>(allFilters);

        while (addedUnderlyings.size() < 20 && yieldIndex < YIELD_LEVELS.length) {
            reportLines.add("These are tier " + tier + " options:");

            List<String> symbols = filterService.getFilteredSymbols(currentFilters);

            // Step 4: Get and sort options
            Specification<Option> spec = Specification.where(OptionSpecifications.isPut())
                    .and(OptionSpecifications.yieldGte(YIELD_LEVELS[yieldIndex]))
                    .and(OptionSpecifications.adjustedPeGte(0.0))
                    .and(OptionSpecifications.underlyingIn(symbols));
            if (strikeMax != null) {
                spec = spec.and(OptionSpecifications.strikeLte(strikeMax));
            }

            List<Option> options = optionRepository.findAll(spec);
            options.sort(Comparator.comparing(Option::getAdjustedPe)
                    .thenComparing(Comparator.comparing(Option::getYield).reversed()));

            // Step 6: Add options until 20 unique underlyings (across all tiers)
            for (Option opt : options) {
                String underlying = opt.getUnderlyingSymbol();
                String symbol = opt.getSymbol();
                if (addedSymbol.add(symbol)) { // Only add if new unique
                    addedUnderlyings.add(underlying);
                    reportLines.add(formatOption(opt));
                    if (addedUnderlyings.size() >= 20) {
                        break;
                    }
                }
            }

            // Step 7: If still <20 unique, remove a filter and reduce yield
            if (addedUnderlyings.size() >= 20) {
                break;
            }

            // Remove next filter (never remove RevenueGrowthFilter)
            boolean removed = false;
            for (Class<?> filterClass : FILTER_REMOVAL_ORDER) {
                Optional<FinancialFilter<?>> toRemove = currentFilters.stream()
                        .filter(f -> filterClass.isInstance(f))
                        .findFirst();
                if (toRemove.isPresent()) {
                    currentFilters.remove(toRemove.get());
                    removed = true;
                    break;
                }
            }

            if (!removed || currentFilters.isEmpty()) { // Only revenue left, or no more to remove
                break;
            }

            yieldIndex++;
            tier++;
        }

        // Step 8: Write to file
        String strikeCap = strikeMax == null ? "_no_cap_" : "_$" + strikeMax.toString() + "_cap_";
        String date = LocalDate.now().toString().replace(":", "-");
        String cap = strikeMax == null ? "/no_cap" : "/" + strikeMax.toString();
        String directory = "logs/reports/" + date + "/" + cap + "/";
        new File(directory).mkdirs();
        String filename = directory + LocalDateTime.now().toString().replace(":", "-") + strikeCap + "put_report_" + ".txt";

        try (FileWriter writer = new FileWriter(filename)) {

            writer.write("Put Option Report - Option Scanner Revenue Data Crunch\n");
            writer.write("Timestamp: " + LocalDateTime.now() + "\n");
            writer.write("Total Options: " + reportLines.size() + "\n\n");

            for (String line : reportLines) {
                writer.write(line + "\n");
            }
        } catch (IOException e) {
            log.error("Failed to write error report: {}", e.getMessage());
        }
        log.info("Report generated with {} unique underlyings.", addedUnderlyings.size());
    }

    private void adjustFiltersForBenchmark(List<FinancialFilter<?>> filters) {
        // Fetch TSLA data once
        List<IncomeStatement> tslaIncome = incomeStatementRepository.findBySymbol(BENCHMARK_SYMBOL); // Add this method to IncomeStatementService if needed: return repo.findBySymbol(symbol);
        List<Earnings> tslaEarnings = earningsRepository.findBySymbol(BENCHMARK_SYMBOL); // Add similar method
        List<BalanceSheet> tslaBalance = balanceSheetRepository.findBySymbol(BENCHMARK_SYMBOL); // Add similar
        List<CashFlow> tslaCashFlow = cashFlowRepository.findBySymbol(BENCHMARK_SYMBOL); // Add similar
        Optional<Asset> tslaAssetOpt = assetRepository.findBySymbol(BENCHMARK_SYMBOL);
        if (tslaAssetOpt.isEmpty()) {
            log.warn("No asset data for benchmark {}", BENCHMARK_SYMBOL);
            return;
        }
        Asset tslaAsset = tslaAssetOpt.get();

        for (FinancialFilter<?> filter : filters) {
            double metric = -1.0;
            if (filter instanceof RevenueGrowthFilter revenueFilter) {
                metric = revenueFilter.calculateCagr(tslaIncome);
                if (metric >= 0 && metric <= revenueFilter.getCagrThreshold()) {
                    double newThreshold = metric - EPSILON;
                    revenueFilter.setCagrThreshold(newThreshold);
                    log.info("Adjusted Revenue CAGR threshold for {} to {}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof EpsGrowthFilter epsFilter) {
                metric = epsFilter.calculateCagr(BENCHMARK_SYMBOL, tslaEarnings);
                if (metric >= 0 && metric <= epsFilter.getCagrThreshold()) {
                    double newThreshold = metric - EPSILON;
                    epsFilter.setCagrThreshold(newThreshold);
                    log.info("Adjusted EPS CAGR threshold for {} to{}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof RoicFilter roicFilter) {
                List<Object> reports = new ArrayList<>();
                reports.addAll(tslaIncome);
                reports.addAll(tslaBalance);
                metric = roicFilter.calculateAverageRoic(BENCHMARK_SYMBOL, reports);
                if (metric >= 0 && metric <= roicFilter.getRoicThreshold()) {
                    double newThreshold = metric - EPSILON;
                    roicFilter.setRoicThreshold(newThreshold);
                    log.info("Adjusted ROIC threshold for {} to {}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof DebtToEquityFilter debtFilter) {
                metric = debtFilter.calculateRatio(BENCHMARK_SYMBOL, tslaBalance);
                if (metric >= 0 && metric >= debtFilter.getDebtToEquityThreshold()) {
                    double newThreshold = metric + EPSILON;
                    debtFilter.setDebtToEquityThreshold(newThreshold);
                    log.info("Adjusted Debt-to-Equity threshold for {} to {}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof FreeCashFlowYieldFilter fcfFilter) {
                List<Object> reports = new ArrayList<>();
                reports.addAll(tslaCashFlow);
                reports.addAll(tslaBalance);
                reports.add(tslaAsset);
                metric = fcfFilter.calculateFcfYield(BENCHMARK_SYMBOL, reports);
                if (metric >= 0 && metric <= fcfFilter.getFcfYieldThreshold()) {
                    double newThreshold = metric - EPSILON;
                    fcfFilter.setFcfYieldThreshold(newThreshold);
                    log.info("Adjusted FCF Yield threshold for {} to {}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof OperatingMarginFilter marginFilter) {
                metric = marginFilter.calculateAverageMargin(BENCHMARK_SYMBOL, tslaIncome);
                if (metric >= 0 && metric <= marginFilter.getMarginThreshold()) {
                    double newThreshold = metric - EPSILON;
                    marginFilter.setMarginThreshold(newThreshold);
                    log.info("Adjusted Operating Margin threshold for {} to {}", BENCHMARK_SYMBOL, newThreshold);
                }
            }
        }
    }

    private String formatOption(Option option) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yy");
        String formattedDate = option.getExpirationDate().format(formatter);
        return String.format(
                "Underlying: %s | Expiration: %s | Strike: %.2f | Previous Close: %.2f | Adjusted PE: %.2f | Yield: %.2f%%",
                option.getUnderlyingSymbol(),
                formattedDate,
                option.getStrike(),
                option.getPreviousClose(),
                option.getAdjustedPe(),
                option.getYield()
        );
    }
}
