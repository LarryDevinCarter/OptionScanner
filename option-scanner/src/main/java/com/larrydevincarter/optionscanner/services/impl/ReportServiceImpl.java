package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.dtos.SoldOptionDTO;
import com.larrydevincarter.optionscanner.models.entities.*;
import com.larrydevincarter.optionscanner.repositories.*;
import com.larrydevincarter.optionscanner.services.*;
import com.larrydevincarter.optionscanner.services.filters.*;
import com.larrydevincarter.optionscanner.utils.OptionSpecifications;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
    @Autowired
    private RestTemplate restTemplate;

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

    @Value("${alpaca.api.base-url}")
    private String alpacaBaseUrl;
    @Value("${alpaca.api.key}")
    private String alpacaApiKey;
    @Value("${alpaca.api.secret}")
    private String apiSecret;

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

        List<FinancialFilter> allFilters = new ArrayList<>(List.of(
                revenueFilter, epsFilter, roicFilter, debtFilter, fcfFilter, marginFilter
        ));

        adjustFiltersForBenchmark(allFilters);

        // Step 2: Generate report
        List<String> reportLines = new ArrayList<>();
        Set<String> addedUnderlyings = new HashSet<>();
        Set<String> addedSymbol = new HashSet<>();
        int tier = 1;
        int yieldIndex = 0;
        List<FinancialFilter> currentFilters = new ArrayList<>(allFilters);

        while (yieldIndex < YIELD_LEVELS.length) {
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
            if (addedUnderlyings.size() == 20) {
                break;
            }

            // Remove next filter (never remove RevenueGrowthFilter)
            boolean removed = false;
            for (Class<?> filterClass : FILTER_REMOVAL_ORDER) {
                Optional<FinancialFilter> toRemove = currentFilters.stream()
                        .filter(filterClass::isInstance)
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

    @Override
    public void generateSoldOptionsReports(List<SoldOptionDTO> dtos) {
        for (SoldOptionDTO dto : dtos) {
            generateSoldOptionReport(dto);
        }
    }

    @Override
    public void generateCoveredCallsReport(String symbol, Double dollarCostAverage) {
        String upperSymbol = symbol.toUpperCase();

        List<Option> calls = optionRepository
                .findByUnderlyingSymbolAndOptionTypeOrderByYieldDesc(upperSymbol, "call");

        List<Option> meaningfulCalls = calls.stream()
                .filter(option -> option.getYield() != null && option.getYield() >= 0.001) // >= 0.10% annualized
                .toList();

        // Create reports directory for today (same as your main report)
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        File reportsDir = new File("logs/reports/" + today + "/covered_calls");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }

        String filename = reportsDir.getPath() + "/" + upperSymbol +
                (dollarCostAverage != null ? "_DCA_" + String.format("%.2f", dollarCostAverage) : "") +
                ".txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {

            writer.write("These are covered call options for owned stock: " + upperSymbol + "\n");
            if (dollarCostAverage != null) {
                writer.write("Dollar Cost Average (Basis): $" + String.format("%.2f", dollarCostAverage) + "\n");
            }
            writer.write("Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n");

            if (meaningfulCalls.isEmpty()) {
                writer.write("No profitable covered calls found above your cost basis at this time.\n");
                log.info("No covered calls found for {}", upperSymbol);
                return;
            }

            // Exact same header format as your main put report
            writer.write(String.format("%-10s | %-12s | %-8s | %-12s | %s%n",
                    "Underlying", "Expiration", "Strike", "Previous Close", "Yield"));
            writer.write("-".repeat(80) + "\n");

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MMM-yy");

            for (Option call : meaningfulCalls) {
                String line = String.format("%-10s | %12s | %8.2f | %12.2f | %6.3f",
                        call.getUnderlyingSymbol(),
                        call.getExpirationDate().format(fmt).toUpperCase(),
                        call.getStrike(),
                        call.getPreviousClose() != null ? call.getPreviousClose() : 0.00,
                        call.getYield() != null ? call.getYield() : 0.000
                );
                writer.write(line + "\n");
            }

            writer.write("\nTotal covered calls found: " + meaningfulCalls.size() + "\n");
            log.info("Covered calls report generated: {}", filename);

        } catch (IOException e) {
            log.error("Failed to generate covered calls report for {}: {}", upperSymbol, e.getMessage());
        }
    }

    private void generateSoldOptionReport(SoldOptionDTO dto) {
        List<LocalDate> tradingDays = getTradingDays(dto.getSoldDate(), dto.getExpirationDate());
        int totalTradingDays = tradingDays.size();
        if (totalTradingDays == 0) {
            log.warn("No trading days found for {} from {} to {}", dto.getUnderlyingSymbol(), dto.getSoldDate(), dto.getExpirationDate());
            return;
        }

        double[] multipliers = {2.0, 1.5, 1.25, 1.12, 1.06, 1.03, 1.01, 1.0};

        List<String> reportLines = new ArrayList<>();
        reportLines.add("Sold Option Buy-to-Close Report - Option Scanner");
        reportLines.add("Underlying: " + dto.getUnderlyingSymbol());
        reportLines.add("Strike: " + dto.getStrikePrice());
        reportLines.add("Expiration: " + dto.getExpirationDate());
        reportLines.add("Type: " + dto.getOptionType());
        reportLines.add("Sold Date: " + dto.getSoldDate());
        reportLines.add("Sold Price: " + String.format("%.2f", dto.getSoldPrice()));
        reportLines.add("Total Trading Days: " + totalTradingDays);
        reportLines.add("Timestamp: " + LocalDateTime.now());
        reportLines.add("");
        reportLines.add(String.format("%-12s %-10s %-12s %-12s", "Date", "Days Held", "Linear BTC", "Accel BTC"));
        reportLines.add("---------------------------------------------------------");

        for (int i = 0; i < totalTradingDays; i++) {
            LocalDate currentDate = tradingDays.get(i);
            int dayHeldSoFar = i + 1;

            // Linear BTC (multiplier = 1.0)
            double linearPrice = calculatePrice(dto.getSoldPrice(), totalTradingDays, dayHeldSoFar, 1.0);
            double flooredLinear = floorToCent(linearPrice);
            if (linearPrice < 0.01) {
                flooredLinear = 0.00;
            }

            // Accelerated BTC
            double accelPrice = 0.00;
            for (double multi : multipliers) {
                double candidatePrice = calculatePrice(dto.getSoldPrice(), totalTradingDays, dayHeldSoFar, multi);
                if (candidatePrice >= 0.01) {
                    accelPrice = floorToCent(candidatePrice);
                    break;
                }
            }
            // If all multipliers give < 0.01, it remains 0.00

            reportLines.add(String.format("%-12s %-10d %-12.2f %-12.2f",
                    currentDate.toString(),
                    dayHeldSoFar,
                    flooredLinear,
                    accelPrice));
        }

        // Write to file
        String date = LocalDate.now().toString();
        String directory = "logs/reports/" + date + "/" + "sold_options/";
        new File(directory).mkdirs();
        String filename = directory + dto.getUnderlyingSymbol() + "_" + dto.getExpirationDate().toString().replace("-", "") + "_" +
                LocalDateTime.now().toString().replace(":", "-").replace(".", "_") + ".txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (String line : reportLines) {
                writer.write(line);
                writer.newLine();
            }
            log.info("Generated sold option report for {} expiring {}", dto.getUnderlyingSymbol(), dto.getExpirationDate());
        } catch (IOException e) {
            log.error("Failed to write sold option report for {}: {}", dto.getUnderlyingSymbol(), e.getMessage());
        }
    }

    private double floorToCent(double price) {
        return Math.floor(price * 100) / 100.0;
    }

    private double calculatePrice(double soldPrice, int totalDays, int dayHeld, double multiplier) {
        double effectiveDaysHeld = dayHeld * multiplier;
        if (effectiveDaysHeld >= totalDays) {
            return 0.0;
        }
        return soldPrice * (totalDays - effectiveDaysHeld) / totalDays;
    }

    private List<LocalDate> getTradingDays(LocalDate soldDate, LocalDate expirationDate) {
        List<LocalDate> tradingDays = new ArrayList<>();
        try {
            String calendarUrl = alpacaBaseUrl + "/v2/calendar?start=" + soldDate + "&end=" + expirationDate;
            HttpHeaders headers = new HttpHeaders();
            headers.set("APCA-API-KEY-ID", alpacaApiKey);
            headers.set("APCA-API-SECRET-KEY", apiSecret);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> calendarResponse = restTemplate.exchange(calendarUrl, HttpMethod.GET, entity, List.class).getBody();
            if (calendarResponse != null) {
                for (Map<String, Object> day : calendarResponse) {
                    tradingDays.add(LocalDate.parse((String) day.get("date")));
                }
                log.info("Fetched {} trading days from calendar", tradingDays.size());
            } else {
                log.warn("Failed to fetch calendar, falling back to no holiday check");
            }
        } catch (Exception e) {
            log.error("Error fetching calendar: {}", e.getMessage());
        }
        return tradingDays;
    }

    private void adjustFiltersForBenchmark(List<FinancialFilter> filters) {
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
        FinancialReports tslaReports = new FinancialReports(tslaIncome, tslaEarnings, tslaBalance, tslaCashFlow, List.of(tslaAsset));

        for (FinancialFilter filter : filters) {
            double metric = -1.0;
            if (filter instanceof RevenueGrowthFilter revenueFilter) {
                metric = revenueFilter.calculateCagr(tslaReports.getIncomeStatements());
                if (metric >= 0 && metric <= revenueFilter.getCagrThreshold()) {
                    double newThreshold = metric - EPSILON;
                    revenueFilter.setCagrThreshold(newThreshold);
                    log.info("Adjusted Revenue CAGR threshold for {} to {}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof EpsGrowthFilter epsFilter) {
                metric = epsFilter.calculateCagr(tslaReports.getEarnings());
                if (metric >= 0 && metric <= epsFilter.getCagrThreshold()) {
                    double newThreshold = metric - EPSILON;
                    epsFilter.setCagrThreshold(newThreshold);
                    log.info("Adjusted EPS CAGR threshold for {} to{}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof RoicFilter roicFilter) {
                metric = roicFilter.calculateAverageRoic(BENCHMARK_SYMBOL, tslaReports);
                if (metric >= 0 && metric <= roicFilter.getRoicThreshold()) {
                    double newThreshold = metric - EPSILON;
                    roicFilter.setRoicThreshold(newThreshold);
                    log.info("Adjusted ROIC threshold for {} to {}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof DebtToEquityFilter debtFilter) {
                metric = debtFilter.calculateRatio(BENCHMARK_SYMBOL, tslaReports.getBalanceSheets());
                if (metric >= 0 && metric >= debtFilter.getDebtToEquityThreshold()) {
                    double newThreshold = metric + EPSILON;
                    debtFilter.setDebtToEquityThreshold(newThreshold);
                    log.info("Adjusted Debt-to-Equity threshold for {} to {}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof FreeCashFlowYieldFilter fcfFilter) {
                metric = fcfFilter.calculateFcfYield(BENCHMARK_SYMBOL, tslaReports);
                if (metric >= 0 && metric <= fcfFilter.getFcfYieldThreshold()) {
                    double newThreshold = metric - EPSILON;
                    fcfFilter.setFcfYieldThreshold(newThreshold);
                    log.info("Adjusted FCF Yield threshold for {} to {}", BENCHMARK_SYMBOL, newThreshold);
                }
            } else if (filter instanceof OperatingMarginFilter marginFilter) {
                metric = marginFilter.calculateAverageMargin(tslaReports.getIncomeStatements());
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
