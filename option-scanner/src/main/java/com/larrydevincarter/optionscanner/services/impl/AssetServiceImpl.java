package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.entities.Asset;
import com.larrydevincarter.optionscanner.models.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.models.entities.CashFlow;
import com.larrydevincarter.optionscanner.models.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.*;
import com.larrydevincarter.optionscanner.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final DividendRepository dividendRepository;
    private final EarningsRepository earningsRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final OptionRepository optionRepository;
    private final EarningsService earningsService;
    private final IncomeStatementService incomeStatementService;
    private final BalanceSheetService balanceSheetService;
    private final CashFlowService cashFlowService;
    private final DividendService dividendService;
    private final MarketService marketService;
    private final OptionService optionService;
    private final ReportService reportService;
    private final UpdateStatusService updateStatusService;
    private final RestTemplate restTemplate;

    @Value("${alpaca.api.key}")
    private String alpacaApiKey;
    @Value("${alpaca.api.secret}")
    private String apiSecret;
    @Value("${alpaca.api.base-url}")
    private String alpacaBaseUrl;
    @Value("${alpaca.data.base-url}")
    private String alpacaDataBaseUrl;

    @Value("${alphavantage.api.key}")
    private String alphavantageApiKey;
    @Value("${alphavantage.api.base-url}")
    private String alphavantageBaseUrl;

    private final List<String> errorLog = new ArrayList<>();
    private static final long DELAY_MS = 60_000;
    private static final long DELAY_BETWEEN_CALLS_MS = 800;
    private static final long DELAY_BETWEEN_CALLS_MS_ALPACA = 300;
    private static final int MAX_RETRIES = 3;

    @Scheduled(cron = "0 0 2 * * ?", zone = "America/Chicago")
    @Override
    public void fetchTradableAssets() {

        errorLog.clear();
        LocalDateTime pullStartTime = LocalDateTime.now();
        log.info("Starting fetching tradable assets");

        updateStatusService.setUpdating(true);

        //TODO: Remove before merge
//        Set<String> testSymbols = new HashSet<>(Arrays.asList("TSLA", "TSM", "WOLF", "META"));

        try {

            String url = alpacaBaseUrl + "/v2/assets?status=active&asset_class=us_equity&attributes=has_options";
            HttpHeaders headers = new HttpHeaders();
            headers.set("APCA-API-KEY-ID", alpacaApiKey);
            headers.set("APCA-API-SECRET-KEY", apiSecret);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            List<Map<String, Object>> assets = restTemplate.exchange(url, HttpMethod.GET, entity, List.class).getBody();

            if (assets != null) {

                for (Map<String, Object> assetData : assets) {

                    String newId = (String) assetData.get("id");
                    String newSymbol = (String) assetData.get("symbol");

                    //TODO: Remove before merge
//                    if (!testSymbols.contains(newSymbol)) {
//                        continue;
//                    }

                    Optional<Asset> existingAssetById = assetRepository.findById(newId);
                    Optional<Asset> existingAssetBySymbol = assetRepository.findBySymbol(newSymbol);

                    if (existingAssetById.isPresent() || existingAssetBySymbol.isPresent()) {

                        Asset oldAsset = existingAssetById.orElseGet(existingAssetBySymbol::get);

                        if (!oldAsset.getId().equals(newId) || !oldAsset.getSymbol().equals(newSymbol)) {

                            log.info("Deleting old asset with symbol {} and id {} before saving new symbol {} and id {}", oldAsset.getSymbol(), oldAsset.getId(), newSymbol, newId);

                            try {
                                deleteAssetAndRelatedRecords(oldAsset.getSymbol(), oldAsset);
                                log.debug("Successfully deleted old asset and related records for symbol {}", oldAsset.getSymbol());
                            } catch (Exception e) {
                                log.error("Failed to delete old asset or related records for symbol {}: {}", oldAsset.getSymbol(), e.getMessage());
                                errorLog.add("Failed to delete old asset or related records for symbol " + oldAsset.getSymbol() + ": " + e.getMessage());
                                continue;
                            }
                        } else {
                            oldAsset.setName((String) assetData.get("name"));
                            oldAsset.setExchange((String) assetData.get("exchange"));
                            oldAsset.setStatus((String) assetData.get("status"));
                            oldAsset.setTradable((Boolean) assetData.get("tradable"));
                            oldAsset.setLastUpdated(LocalDateTime.now());
                            assetRepository.save(oldAsset);
                            continue;
                        }
                    }
                    Asset asset = new Asset();
                    asset.setId((String) assetData.get("id"));
                    asset.setSymbol((String) assetData.get("symbol"));
                    asset.setName((String) assetData.get("name"));
                    asset.setExchange((String) assetData.get("exchange"));
                    asset.setStatus((String) assetData.get("status"));
                    asset.setTradable((Boolean) assetData.get("tradable"));
                    asset.setLastUpdated(LocalDateTime.now());
                    log.info("Asset id: {}, symbol: {}, name: {}, exchange: {}, status: {}, tradable: {}", asset.getId(), asset.getSymbol(), asset.getName(), asset.getExchange(), asset.getStatus(), asset.isTradable());
                    assetRepository.save(asset);
                }
                log.info("Processed {} assets", assets.size());
            }
        } catch (Exception e) {
            log.error("Failed to fetch tradable assets: {}", e.getMessage());
            errorLog.add("Failed to fetch tradable assets: " + e.getMessage());
            writeErrorReport();
            //TODO: Implement retry logic
            return;
        }
        List<Asset> staleAssets = assetRepository.findActiveStaleAssets(pullStartTime);

        for (Asset staleAsset : staleAssets) {

            //TODO: Remove before merge
//            if (!testSymbols.contains(staleAsset.getSymbol())) {
//                continue;
//            }
            try {

                String url = alpacaBaseUrl + "/v2/assets/" + staleAsset.getId();
                HttpHeaders headers = new HttpHeaders();
                headers.set("APCA-API-KEY-ID", alpacaApiKey);
                headers.set("APCA-API-SECRET-KEY", apiSecret);
                HttpEntity<String> entity = new HttpEntity<>(headers);
                Map<String, Object> assetData = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();

                if (assetData != null) {

                    String symbol = (String) assetData.get("symbol");
                    Optional<Asset> existingAsset = assetRepository.findBySymbol(symbol);

                    if (existingAsset.isPresent() && !existingAsset.get().getId().equals(staleAsset.getId())) {

                        log.info("Deleting stale asset with symbol {} and id {} due to new asset", symbol, staleAsset.getId());

                        try {
                            deleteAssetAndRelatedRecords(symbol, staleAsset);
                            log.debug("Successfully deleted stale asset and related records for symbol {}", symbol);
                        } catch (Exception e) {
                            log.error("Failed to delete stale asset or related records for symbol {}: {}", symbol, e.getMessage());
                            errorLog.add("Failed to delete stale asset or related records for symbol " + symbol + ": " + e.getMessage());
                        }
                    } else {
                        staleAsset.setStatus((String) assetData.get("status"));
                        staleAsset.setTradable((Boolean) assetData.get("tradable"));
                        staleAsset.setLastUpdated(LocalDateTime.now());
                        assetRepository.save(staleAsset);
                        log.info("Updated stale asset {} to status {}", staleAsset.getSymbol(), staleAsset.getStatus());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to update stale asset {}: {}", staleAsset.getSymbol(), e.getMessage());
                errorLog.add("Failed to update stale asset " + staleAsset.getSymbol() + ": " + e.getMessage());
            }
        }
        log.info("Checked {} stale active assets", staleAssets.size());

        //TODO: uncomment after testing
        List<String> symbols = assetRepository.findActiveTradableSymbols();
        symbols = fetchAndStoreFinancialReport(symbols, incomeStatementService);
        //TODO: comment after testing
//        List<String> symbols = fetchAndStoreFinancialReport(new ArrayList<>(testSymbols), incomeStatementService);

        sleepBetweenStages();
        symbols = fetchAndStoreFinancialReport(symbols, earningsService);
        sleepBetweenStages();

        symbols = fetchAndStoreFinancialReport(symbols, balanceSheetService);
        sleepBetweenStages();

        symbols = fetchAndStoreFinancialReport(symbols, cashFlowService);
        sleepBetweenStages();

        fetchAndStoreFinancialReport(symbols, dividendService);
        sleepBetweenStages();

        fetchAndStoreStockPrices(errorLog, symbols);
        sleepBetweenStages();

        fetchAndStoreOptions(errorLog, symbols);
        updateStatusService.setUpdating(false);
        log.info("Database update completed (flag reset)");
        writeErrorReport();
        reportService.generateReport(null);

    }

    @Transactional
    private void deleteAssetAndRelatedRecords(String symbol, Asset asset) {
        try {
            log.debug("Deleting related records for symbol {}", symbol);

            earningsRepository.deleteBySymbol(symbol);
            log.debug("Deleted earnings for symbol {}", symbol);

            incomeStatementRepository.deleteBySymbol(symbol);
            log.debug("Deleted income statements for symbol {}", symbol);

            balanceSheetRepository.deleteBySymbol(symbol);
            log.debug("Deleted balance sheets for symbol {}", symbol);

            cashFlowRepository.deleteBySymbol(symbol);
            log.debug("Deleted cash flows for symbol {}", symbol);

            optionRepository.deleteByUnderlyingSymbol(symbol);
            log.debug("Deleted options for symbol {}", symbol);

            dividendRepository.deleteBySymbol(symbol);
            log.debug("Deleted dividends for symbol {}", symbol);

            assetRepository.deleteBySymbol(symbol);
            log.debug("Deleted asset for symbol {}", symbol);
        } catch (Exception e) {
            log.error("Failed to delete records for symbol {}: {}", symbol, e.getMessage());
            errorLog.add("Failed to delete records for symbol " + symbol + ": " + e.getMessage());
            throw new RuntimeException("Deletion failed for symbol " + symbol, e);
        }
    }

    public void writeErrorReport() {

        if (errorLog.isEmpty()) {
            log.info("No errors to report.");
            return;
        }
        String directory = "logs/errors/";
        new File(directory).mkdirs();
        String filename = directory + "error_report_" + LocalDateTime.now().toString().replace(":", "-") + ".txt";

        try (FileWriter writer = new FileWriter(filename)) {

            writer.write("Error Report - Option Scanner Revenue Data Fetch\n");
            writer.write("Timestamp: " + LocalDateTime.now() + "\n");
            writer.write("Total Errors: " + errorLog.size() + "\n\n");

            for (String error : errorLog) {
                writer.write(error + "\n");
            }
            log.info("Error report written to {}", filename);
        } catch (IOException e) {
            log.error("Failed to write error report: {}", e.getMessage());
        }
    }

    @Override
    public void fetchAndStoreStockPrices(List<String> errorLog, List<String> symbols) {

        List<Asset> assets = assetRepository.findBySymbols(symbols);
        log.info("Fetching stock prices for {} active, tradable assets", assets.size());

        for (Asset asset : assets) {

            String url = String.format("%s/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                    alphavantageBaseUrl, asset.getSymbol(), alphavantageApiKey);
            Map<String, Object> responseBody = null;
            int attempt = 0;

            while (attempt < MAX_RETRIES) {
                try {
                    responseBody = restTemplate.getForObject(url, Map.class);
                    try {
                        Thread.sleep(DELAY_BETWEEN_CALLS_MS);
                    } catch (InterruptedException e) {
                        log.error("Interrupted during rate limit delay: {}", e.getMessage());
                        errorLog.add("Interrupted during rate limit delay for symbol " + asset.getSymbol() + ": " + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                    break;
                } catch (ResourceAccessException | HttpServerErrorException e) {
                    attempt++;
                    String errorMsg = String.format("Attempt %d failed for %s: %s%s",
                            attempt, asset.getSymbol(), e.getMessage(),
                            e instanceof HttpServerErrorException ? " (HTTP Status: " + ((HttpServerErrorException) e).getStatusCode() + ")" : "");
                    log.warn(errorMsg);
                    errorLog.add(errorMsg);

                    if (attempt == MAX_RETRIES) {
                        errorLog.add("Max retries reached for " + asset.getSymbol() + ". Skipping.");
                        break;
                    }
                    try {
                        Thread.sleep(DELAY_MS / 2);
                    } catch (InterruptedException ie) {
                        log.error("Interrupted during retry delay: {}", ie.getMessage());
                        errorLog.add("Interrupted during retry delay for " + asset.getSymbol() + ": " + ie.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            Map<String, String> quote = new HashMap<>();
            if (responseBody != null && responseBody.containsKey("Global Quote")) {
                quote = (Map<String, String>) responseBody.get("Global Quote");
                String priceStr = quote.get("05. price");
                if (priceStr != null && !priceStr.trim().isEmpty()) {
                    try {
                        Double price = processStockPrice(priceStr, asset);
                        log.info("Stored stock price {} for symbol {}", price, asset.getSymbol());
                        computeAndStoreAdjustedMetrics(asset, errorLog);
                    } catch (NumberFormatException e) {
                        log.error("Failed to parse price for symbol {}: {}", asset.getSymbol(), priceStr);
                        errorLog.add("Failed to parse price for symbol " + asset.getSymbol() + ": " + priceStr);
                        asset.setStatus("inactive");
                        asset.setLastUpdated(LocalDateTime.now());
                        assetRepository.save(asset);
                        log.info("Marked asset {} as inactive due to invalid price format", asset.getSymbol());
                    }
                } else {
                    log.error("Missing or empty price for symbol {}", asset.getSymbol());
                    errorLog.add("Missing or empty price for symbol: " + asset.getSymbol());
                    asset.setStatus("inactive");
                    asset.setLastUpdated(LocalDateTime.now());
                    assetRepository.save(asset);
                    log.info("Marked asset {} as inactive due to missing or empty price", asset.getSymbol());
                }
            } else {
                log.error("Missing or empty price for symbol {}: full quote = {}", asset.getSymbol(), quote);
                errorLog.add("Missing or empty price for symbol: " + asset.getSymbol() + " | Quote: " + quote);
                asset.setStatus("inactive");
                asset.setLastUpdated(LocalDateTime.now());
                assetRepository.save(asset);
                log.info("Marked asset {} as inactive due to failed price fetch", asset.getSymbol());
            }
        }
        log.info("Completed fetching stock prices");
    }

    @Override
    @Transactional
    public void fetchAndStoreOptions(List<String> errorLog, List<String> symbols) {
        List<Asset> assets = assetRepository.findBySymbols(symbols);
        log.info("Starting fetching put options for {} assets", assets.size());
        LocalDate today = LocalDate.now();
        Set<LocalDate> tradingDays = marketService.getTradingDays();


        LocalDate previousTradingDay = tradingDays.stream()
                .filter(d -> d.isBefore(today))
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);

        if (previousTradingDay == null) {
            log.warn("No previous trading day found; skipping options fetch");
            errorLog.add("No previous trading day found");
            return;
        }

        for (Asset asset : assets) {
            optionService.processOptionsForSymbol(asset.getSymbol(), errorLog, tradingDays, previousTradingDay);
            try {
                Thread.sleep(DELAY_BETWEEN_CALLS_MS_ALPACA);
            } catch (InterruptedException e) {
                log.error("Interrupted during rate limit delay: {}", e.getMessage());
                errorLog.add("Interrupted during rate limit delay for asset " + asset.getSymbol() + ": " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        log.info("Completed fetching put options");
    }

    private void computeAndStoreAdjustedMetrics(Asset asset, List<String> errorLog) {
        try {
            IncomeStatement latestIncome = incomeStatementRepository
                    .findTopBySymbolAndReportTypeOrderByFiscalDateEndingDesc(asset.getSymbol(), "annual")
                    .orElseThrow(() -> new NoSuchElementException("No annual income statement found"));
            CashFlow latestCashFlow = cashFlowRepository
                    .findTopBySymbolAndReportTypeOrderByFiscalDateEndingDesc(asset.getSymbol(), "annual")
                    .orElseThrow(() -> new NoSuchElementException("No annual cash flow found"));
            BalanceSheet latestBalance = balanceSheetRepository
                    .findTopBySymbolAndReportTypeOrderByFiscalDateEndingDesc(asset.getSymbol(), "annual")
                    .orElseThrow(() -> new NoSuchElementException("No annual balance sheet found"));
            Double netIncome = latestIncome.getNetIncome();
            Double researchAndDevelopment = latestIncome.getResearchAndDevelopment();
            Double capitalExpenditures = latestCashFlow.getCapitalExpenditures();
            Double shares = latestBalance.getCommonStockSharesOutstanding();

            if (netIncome == null || researchAndDevelopment == null || capitalExpenditures == null || shares == null) {
                log.warn("Null values found in financial metrics for symbol {}: skipping adjusted metrics computation", asset.getSymbol());
                return;
            }
            double adjustedNetIncomeVal = netIncome + researchAndDevelopment + capitalExpenditures;
            double adjustedEps = (shares != 0) ? adjustedNetIncomeVal / shares : 0.0;
            asset.setAdjustedNetIncome(adjustedNetIncomeVal);
            asset.setAdjustedEarningsPerShare(adjustedEps);
            assetRepository.save(asset);
            log.info("Stored adjusted net income {} and EPS {} for symbol {}", adjustedNetIncomeVal, adjustedEps, asset.getSymbol());
        } catch (IncorrectResultSizeDataAccessException e) {
            log.error("Non-unique result for symbol {}: {}", asset.getSymbol(), e.getMessage());
            errorLog.add("Non-unique result for symbol " + asset.getSymbol() + ": " + e.getMessage());
        } catch (NoSuchElementException e) {
            log.warn("Missing latest annual statements for symbol {}: {}", asset.getSymbol(), e.getMessage());
            errorLog.add("Missing latest annual statements for " + asset.getSymbol() + ": " + e.getMessage());
        }
    }

    @Transactional
    private Double processStockPrice(String priceStr, Asset asset) {
        Double price = Double.parseDouble(priceStr);
        asset.setCurrentPrice(price);
        asset.setLastPriceUpdated(LocalDateTime.now());
        if (asset.getSharesOutstanding() != null) {
            asset.setMarketCap(price * asset.getSharesOutstanding());
        }
        assetRepository.save(asset);
        return price;
    }

    private Map<String, Object> fetchAlphaVantageReport(
            String symbol, String functionName, List<String> errorLog) {

        String url = String.format("%s/query?function=%s&symbol=%s&apikey=%s",
                alphavantageBaseUrl, functionName, symbol, alphavantageApiKey);

        Map<String, Object> responseBody = null;
        int attempt = 0;
        boolean hasLongPaused = false;

        while (attempt < MAX_RETRIES) {
            try {
                responseBody = restTemplate.getForObject(url, Map.class);

                try {
                    Thread.sleep(DELAY_BETWEEN_CALLS_MS);
                } catch (InterruptedException e) {
                    log.error("Interrupted during rate limit delay for {}: {}", symbol, e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for " + symbol + ": " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
                break;

            } catch (ResourceAccessException | HttpServerErrorException e) {
                attempt++;
                String errorMsg = String.format("Attempt %d failed for %s (%s): %s%s",
                        attempt, symbol, functionName, e.getMessage(),
                        (e instanceof HttpServerErrorException) ? " (HTTP " + ((HttpServerErrorException) e).getStatusCode() + ")" : "");

                log.warn(errorMsg);
                errorLog.add(errorMsg);

                if (attempt == MAX_RETRIES) {
                    errorLog.add("Max retries reached for " + symbol + " (" + functionName + "). Skipping.");
                    break;
                }

                long pauseDuration = DELAY_MS / 2;
                if (!hasLongPaused && e instanceof HttpServerErrorException &&
                        ((HttpServerErrorException) e).getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                    pauseDuration = 30 * 60 * 1000L; // 30 minutes
                    log.info("Detected 503 for {}. Pausing 30 minutes...", symbol);
                    errorLog.add("Detected 503 for " + symbol + ". Pausing 30 minutes.");
                    hasLongPaused = true;
                }

                try {
                    Thread.sleep(pauseDuration);
                } catch (InterruptedException ie) {
                    log.error("Interrupted during retry delay: {}", ie.getMessage());
                    errorLog.add("Interrupted during retry delay for " + symbol + ": " + ie.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return responseBody;
    }

    private List<String> fetchAndStoreFinancialReport(
            List<String> symbols,
            AlphaVantageReportService service) {

        List<String> needingUpdate = service.getSymbolsNeedingUpdate(symbols);
        String reportName = service.getReportDisplayName();

        log.info("Number of symbols to update {}: {}", reportName.toUpperCase(), needingUpdate.size());

        for (String symbol : needingUpdate) {
            Map<String, Object> responseBody = fetchAlphaVantageReport(
                    symbol, service.getFunctionName(), errorLog);

            if (responseBody != null) {
                try {
                    service.processReport(symbol, responseBody, errorLog);
                } catch (Exception e) {
                    log.error("Failed to process {} for symbol {}: {}", reportName, symbol, e.getMessage());
                    errorLog.add("Failed to process " + reportName + " for " + symbol + ": " + e.getMessage());
                }
            } else {
                errorLog.add("Failed to fetch " + reportName + " for symbol: " + symbol + " after " + MAX_RETRIES + " attempts.");
            }
        }

        log.info("Completed fetching {}", reportName);
        return service.getSymbolsThatHaveData(symbols);
    }

    private void sleepBetweenStages() {
        try {
            Thread.sleep(DELAY_BETWEEN_CALLS_MS);
        } catch (InterruptedException e) {
            log.error("Interrupted during stage transition delay: {}", e.getMessage());
            errorLog.add("Interrupted during stage transition delay: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}