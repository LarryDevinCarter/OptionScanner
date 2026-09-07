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
import org.springframework.data.domain.PageRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Background data engine: syncs assets and refreshes fundamentals/prices/options in batches.
 * Does not lock out API reads during refresh — last-good rows remain queryable.
 */
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

    /** Free AV tier ~25 calls/min => >= 2400ms; default 2500ms. */
    @Value("${alphavantage.delay-ms:2500}")
    private long delayBetweenCallsMs;

    @Value("${optionscanner.refresh.batch-size:40}")
    private int refreshBatchSize;

    @Value("${optionscanner.refresh.sync-universe:true}")
    private boolean syncUniverseEachTick;

    private final List<String> errorLog = new ArrayList<>();
    private static final long DELAY_MS = 60_000;
    private static final long DELAY_BETWEEN_CALLS_MS_ALPACA = 300;
    private static final int MAX_RETRIES = 3;

    /**
     * Configurable cron (default: top of every hour, America/Chicago).
     * Each tick refreshes only {@code optionscanner.refresh.batch-size} symbols
     * ordered by oldest {@code lastUpdated} (round-robin staleness).
     */
    @Scheduled(
            cron = "${optionscanner.refresh.cron:0 0 * * * ?}",
            zone = "${optionscanner.refresh.zone:America/Chicago}"
    )
    @Override
    public void fetchTradableAssets() {

        if (!updateStatusService.tryBeginRefresh()) {
            log.info("Refresh already in progress; skipping this scheduled tick (reads stay available)");
            return;
        }

        errorLog.clear();
        log.info("Starting batched asset refresh (batchSize={}, avDelayMs={})",
                refreshBatchSize, delayBetweenCallsMs);

        try {
            Set<String> seenIds = new HashSet<>();
            if (syncUniverseEachTick) {
                seenIds = syncTradableUniverseFromAlpaca();
                reconcileAssetsMissingFromUniverse(seenIds);
            }

            List<String> symbols = assetRepository.findActiveTradableSymbolsOldestFirst(
                    PageRequest.of(0, Math.max(1, refreshBatchSize)));
            log.info("Selected {} symbols for this refresh tick (oldest lastUpdated first)", symbols.size());

            if (symbols.isEmpty()) {
                log.warn("No active tradable symbols to refresh");
                return;
            }

            // Per-symbol incremental persistence: each report service replaces rows for one symbol only.
            symbols = fetchAndStoreFinancialReport(symbols, incomeStatementService);
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

            touchLastUpdated(symbols);
            log.info("Batched refresh completed for {} symbols (DB remained readable throughout)", symbols.size());
            writeErrorReport();
            reportService.generateReport(null);
        } catch (Exception e) {
            log.error("Refresh tick failed: {}", e.getMessage(), e);
            errorLog.add("Refresh tick failed: " + e.getMessage());
            writeErrorReport();
        } finally {
            // Informational only — never used to lock out GET/POST data endpoints
            updateStatusService.markRefreshFinished();
        }
    }

    /**
     * Upsert Alpaca universe without wiping the table. Does not bump lastUpdated on
     * unchanged symbols so staleness-based round-robin keeps working.
     */
    @SuppressWarnings("unchecked")
    private Set<String> syncTradableUniverseFromAlpaca() {
        Set<String> seenIds = new HashSet<>();
        try {
            String url = alpacaBaseUrl + "/v2/assets?status=active&asset_class=us_equity&attributes=has_options";
            HttpHeaders headers = new HttpHeaders();
            headers.set("APCA-API-KEY-ID", alpacaApiKey);
            headers.set("APCA-API-SECRET-KEY", apiSecret);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            List<Map<String, Object>> assets = restTemplate.exchange(url, HttpMethod.GET, entity, List.class).getBody();

            if (assets == null) {
                return seenIds;
            }

            for (Map<String, Object> assetData : assets) {
                String newId = (String) assetData.get("id");
                String newSymbol = (String) assetData.get("symbol");
                if (newId != null) {
                    seenIds.add(newId);
                }

                Optional<Asset> existingAssetById = assetRepository.findById(newId);
                Optional<Asset> existingAssetBySymbol = assetRepository.findBySymbol(newSymbol);

                if (existingAssetById.isPresent() || existingAssetBySymbol.isPresent()) {
                    Asset oldAsset = existingAssetById.orElseGet(existingAssetBySymbol::get);

                    if (!oldAsset.getId().equals(newId) || !oldAsset.getSymbol().equals(newSymbol)) {
                        log.info("Deleting old asset with symbol {} and id {} before saving new symbol {} and id {}",
                                oldAsset.getSymbol(), oldAsset.getId(), newSymbol, newId);
                        try {
                            deleteAssetAndRelatedRecords(oldAsset.getSymbol(), oldAsset);
                        } catch (Exception e) {
                            log.error("Failed to delete old asset or related records for symbol {}: {}",
                                    oldAsset.getSymbol(), e.getMessage());
                            errorLog.add("Failed to delete old asset or related records for symbol "
                                    + oldAsset.getSymbol() + ": " + e.getMessage());
                            continue;
                        }
                    } else {
                        // Metadata only — preserve lastUpdated for round-robin
                        oldAsset.setName((String) assetData.get("name"));
                        oldAsset.setExchange((String) assetData.get("exchange"));
                        oldAsset.setStatus((String) assetData.get("status"));
                        oldAsset.setTradable((Boolean) assetData.get("tradable"));
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
                // Epoch so new symbols are prioritized in the next batches
                asset.setLastUpdated(LocalDateTime.of(1970, 1, 1, 0, 0));
                log.info("New asset id: {}, symbol: {}", asset.getId(), asset.getSymbol());
                assetRepository.save(asset);
            }
            log.info("Synced universe: processed {} Alpaca assets ({} ids tracked)", assets.size(), seenIds.size());
        } catch (Exception e) {
            log.error("Failed to fetch tradable assets: {}", e.getMessage());
            errorLog.add("Failed to fetch tradable assets: " + e.getMessage());
        }
        return seenIds;
    }

    @SuppressWarnings("unchecked")
    private void reconcileAssetsMissingFromUniverse(Set<String> seenIds) {
        if (seenIds == null || seenIds.isEmpty()) {
            return;
        }
        List<Asset> missing = assetRepository.findActiveAssetsNotInIds(seenIds);
        for (Asset staleAsset : missing) {
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
                        log.info("Deleting stale asset with symbol {} and id {} due to new asset",
                                symbol, staleAsset.getId());
                        try {
                            deleteAssetAndRelatedRecords(symbol, staleAsset);
                        } catch (Exception e) {
                            log.error("Failed to delete stale asset or related records for symbol {}: {}",
                                    symbol, e.getMessage());
                            errorLog.add("Failed to delete stale asset or related records for symbol "
                                    + symbol + ": " + e.getMessage());
                        }
                    } else {
                        staleAsset.setStatus((String) assetData.get("status"));
                        staleAsset.setTradable((Boolean) assetData.get("tradable"));
                        // Do not bump lastUpdated here — keep staleness priority
                        assetRepository.save(staleAsset);
                        log.info("Updated missing-from-universe asset {} to status {}",
                                staleAsset.getSymbol(), staleAsset.getStatus());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to update stale asset {}: {}", staleAsset.getSymbol(), e.getMessage());
                errorLog.add("Failed to update stale asset " + staleAsset.getSymbol() + ": " + e.getMessage());
            }
        }
        log.info("Reconciled {} assets missing from latest Alpaca universe snapshot", missing.size());
    }

    private void touchLastUpdated(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Asset asset : assetRepository.findBySymbols(symbols)) {
            asset.setLastUpdated(now);
            assetRepository.save(asset);
        }
    }

    @Transactional
    private void deleteAssetAndRelatedRecords(String symbol, Asset asset) {
        try {
            log.debug("Deleting related records for symbol {}", symbol);

            earningsRepository.deleteBySymbol(symbol);
            incomeStatementRepository.deleteBySymbol(symbol);
            balanceSheetRepository.deleteBySymbol(symbol);
            cashFlowRepository.deleteBySymbol(symbol);
            optionRepository.deleteByUnderlyingSymbol(symbol);
            dividendRepository.deleteBySymbol(symbol);
            assetRepository.deleteBySymbol(symbol);
            log.debug("Deleted asset and related records for symbol {}", symbol);
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
    @SuppressWarnings("unchecked")
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
                        Thread.sleep(delayBetweenCallsMs);
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
                        assetRepository.save(asset);
                        log.info("Marked asset {} as inactive due to invalid price format", asset.getSymbol());
                    }
                } else {
                    log.error("Missing or empty price for symbol {}", asset.getSymbol());
                    errorLog.add("Missing or empty price for symbol: " + asset.getSymbol());
                    asset.setStatus("inactive");
                    assetRepository.save(asset);
                    log.info("Marked asset {} as inactive due to missing or empty price", asset.getSymbol());
                }
            } else {
                log.error("Missing or empty price for symbol {}: full quote = {}", asset.getSymbol(), quote);
                errorLog.add("Missing or empty price for symbol: " + asset.getSymbol() + " | Quote: " + quote);
                asset.setStatus("inactive");
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

    @SuppressWarnings("unchecked")
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
                    Thread.sleep(delayBetweenCallsMs);
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
            Thread.sleep(delayBetweenCallsMs);
        } catch (InterruptedException e) {
            log.error("Interrupted during stage transition delay: {}", e.getMessage());
            errorLog.add("Interrupted during stage transition delay: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
