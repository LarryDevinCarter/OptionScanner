package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.Asset;
import com.larrydevincarter.optionscanner.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.entities.CashFlow;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.*;
import com.larrydevincarter.optionscanner.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final EarningsRepository earningsRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final BalanceSheetRepository balanceSheetRepository;
    private final CashFlowRepository cashFlowRepository;
    private final EarningsService earningsService;
    private final IncomeStatementService incomeStatementService;
    private final BalanceSheetService balanceSheetService;
    private final CashFlowService cashFlowService;
    private final DividendService dividendService;
    private final RestTemplate restTemplate;

    @Value("${alpaca.api.key}")
    private String alpacaApiKey;
    @Value("${alpaca.api.secret}")
    private String apiSecret;
    @Value("${alpaca.api.base-url}")
    private String alpacaBaseUrl;

    @Value("${alphavantage.api.key}")
    private String alphavantageApiKey;
    @Value("${alphavantage.api.base-url}")
    private String alphavantageBaseUrl;

    private final List<String> errorLog = new ArrayList<>();
    private static final long DELAY_MS = 60_000;
    private static final int CALLS_PER_MINUTE = 75;
    private static final int MAX_RETRIES = 3;

    @Scheduled(cron = "0 0 2 * * ?", zone = "America/Chicago")
    @Override
    public void fetchTradableAssets() {

        LocalDateTime pullStartTime = LocalDateTime.now();
        log.info("Starting fetching tradable assets");

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
            return;
        }
        List<Asset> staleAssets = assetRepository.findActiveStaleAssets(pullStartTime);

        for (Asset staleAsset : staleAssets) {
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
        List<String> symbols = assetRepository.findActiveTradableSymbols();
        symbols = fetchAndStoreIncomeStatements(errorLog, symbols);

        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            log.error("Interrupted during rate limit delay: {}", e.getMessage());
            errorLog.add("Interrupted during rate limit delay while transitioning from fetching income statement to fetching earnings: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        symbols = fetchAndStoreEarnings(errorLog, symbols);

        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            log.error("Interrupted during rate limit delay: {}", e.getMessage());
            errorLog.add("Interrupted during rate limit delay while transitioning from fetching earnings to fetching balance sheets: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        symbols = fetchAndStoreBalanceSheets(errorLog, symbols);

        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            log.error("Interrupted during rate limit delay: {}", e.getMessage());
            errorLog.add("Interrupted during rate limit delay while transitioning from fetching balance sheets to fetching cash flows: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        symbols = fetchAndStoreCashFlows(errorLog, symbols);

        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            log.error("Interrupted during rate limit delay: {}", e.getMessage());
            errorLog.add("Interrupted during rate limit delay while transitioning from fetching balance sheets to fetching cash flows: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        fetchAndStoreDividends(errorLog, symbols);

        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            log.error("Interrupted during rate limit delay: {}", e.getMessage());
            errorLog.add("Interrupted during rate limit delay while transitioning from fetching dividends to fetching stock prices: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        fetchAndStoreStockPrices(errorLog, symbols);
        writeErrorReport();
    }

    @Transactional
    private void deleteAssetAndRelatedRecords(String symbol, Asset asset) {
        earningsRepository.deleteBySymbol(symbol);
        incomeStatementRepository.deleteBySymbol(symbol);
        balanceSheetRepository.deleteBySymbol(symbol);
        assetRepository.delete(asset);
    }

    private void writeErrorReport() {

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
    public List<String> fetchAndStoreIncomeStatements(List<String> errorLog, List<String> symbols) {

        errorLog.clear();
        List<String> symbolsNeedingUpdate = incomeStatementService.getSymbolsNeedingUpdate(symbols);
        log.info("Number of Symbols to update INCOME_STATEMENTS for: {}", symbolsNeedingUpdate.size());
        int callCount = 0;
        log.info("Starting fetching income statements");

        for (String symbol : symbolsNeedingUpdate) {

            if (callCount >= CALLS_PER_MINUTE) {

                log.info("Hit rate limit (75 calls/minute). Pausing for 1 minute...");

                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    log.error("Interrupted during rate limit delay: {}", e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for symbol " + symbol + ": " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
                callCount = 0;
            }
            String url = String.format("%s/query?function=INCOME_STATEMENT&symbol=%s&apikey=%s", alphavantageBaseUrl, symbol, alphavantageApiKey);
            Map<String, Object> responseBody = null;
            int attempt = 0;
            boolean hasLongPaused = false;

            while (attempt < MAX_RETRIES) {
                try {
                    responseBody = restTemplate.getForObject(url, Map.class);
                    callCount++;
                    break;
                } catch (ResourceAccessException | HttpServerErrorException e) {

                    attempt++;
                    String errorMsg = String.format("Attempt %d failed for %s: %s%s",
                            attempt, symbol, e.getMessage(),
                            e instanceof HttpServerErrorException ? " (HTTP Status: " + ((HttpServerErrorException) e).getStatusCode() + ")" : "");
                    log.warn(errorMsg);
                    errorLog.add(errorMsg);

                    if (attempt == MAX_RETRIES) {
                        errorLog.add("Max retries reached for " + symbol + ". Skipping.");
                        break;
                    }
                    long pauseDuration = DELAY_MS / 2;

                    if (!hasLongPaused && e instanceof HttpServerErrorException &&
                            ((HttpServerErrorException) e).getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        pauseDuration = 30 * 60 * 1000;
                        log.info("Detected 503 Service Unavailable for {}. Pausing for 30 minutes before retry...", symbol);
                        errorLog.add("Detected 503 for " + symbol + ". Pausing for 30 minutes.");
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
            if (responseBody != null) {
                try {
                    incomeStatementService.processIncomeStatements(symbol, responseBody, errorLog);
                } catch (Exception e) {
                    log.error("Failed to process income statements for symbol {}: {}", symbol, e.getMessage());
                    errorLog.add("Failed to process income statements for " + symbol + ": " + e.getMessage());
                }
            } else {
                errorLog.add("Failed to fetch income statement for symbol: " + symbol + " after " + MAX_RETRIES + " attempts.");
            }
        }
        log.info("Completed fetching income statements");
        return incomeStatementService.getSymbolsThatHaveStatements(symbols);
    }

    @Override
    public List<String> fetchAndStoreEarnings(List<String> errorLog, List<String> symbols) {

        List<String> symbolsNeedingUpdate = earningsService.getSymbolsNeedingUpdate(symbols);
        log.info("Fetching earnings for {} symbols with updated income statements", symbolsNeedingUpdate.size());
        int callCount = 0;

        for (String symbol : symbolsNeedingUpdate) {
            if (callCount >= CALLS_PER_MINUTE) {

                log.info("Hit rate limit (75 calls/minute). Pausing for 1 minute...");

                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    log.error("Interrupted during rate limit delay: {}", e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for symbol " + symbol + ": " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
                callCount = 0;
            }

            String url = String.format("%s/query?function=EARNINGS&symbol=%s&apikey=%s", alphavantageBaseUrl, symbol, alphavantageApiKey);
            Map<String, Object> responseBody = null;
            int attempt = 0;
            boolean hasLongPaused = false;

            while (attempt < MAX_RETRIES) {
                try {
                    responseBody = restTemplate.getForObject(url, Map.class);
                    callCount++;
                    break;
                } catch (ResourceAccessException | HttpServerErrorException e) {
                    attempt++;
                    String errorMsg = String.format("Attempt %d failed for %s: %s%s",
                            attempt, symbol, e.getMessage(),
                            e instanceof HttpServerErrorException ? " (HTTP Status: " + ((HttpServerErrorException) e).getStatusCode() + ")" : "");
                    log.warn(errorMsg);
                    errorLog.add(errorMsg);

                    if (attempt == MAX_RETRIES) {
                        errorLog.add("Max retries reached for " + symbol + ". Skipping.");
                        break;
                    }
                    long pauseDuration = 1000;

                    if (!hasLongPaused && e instanceof HttpServerErrorException &&
                            ((HttpServerErrorException) e).getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        pauseDuration = 30 * 60 * 1000;
                        log.info("Detected 503 Service Unavailable for {}. Pausing for 30 minutes before retry...", symbol);
                        errorLog.add("Detected 503 for " + symbol + ". Pausing for 30 minutes.");
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
            if (responseBody != null) {
                try {
                    earningsService.processEarnings(symbol, responseBody, errorLog);
                } catch (Exception e) {
                    log.error("Failed to process earnings for symbol {}: {}", symbol, e.getMessage());
                    errorLog.add("Failed to process earnings for symbol " + symbol + ": " + e.getMessage());
                }
            } else {
                errorLog.add("Failed to fetch earnings for symbol: " + symbol + " after " + MAX_RETRIES + " attempts.");
            }
        }
        log.info("Completed fetching and storing earnings for all symbols");
        return earningsRepository.findSymbolsThatHaveStatements(symbols);
    }

    @Override
    public List<String> fetchAndStoreBalanceSheets(List<String> errorLog, List<String> symbols) {

        List<String> symbolsNeedingUpdate = balanceSheetService.getSymbolsNeedingUpdate(symbols);
        log.info("Number of Symbols to update BALANCE_SHEETS for: {}", symbolsNeedingUpdate.size());
        int callCount = 0;
        log.info("Starting fetching balance sheets");

        for (String symbol : symbolsNeedingUpdate) {

            if (callCount >= CALLS_PER_MINUTE) {

                log.info("Hit rate limit (75 calls/minute). Pausing for 1 minute...");

                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    log.error("Interrupted during rate limit delay: {}", e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for symbol " + symbol + ": " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
                callCount = 0;
            }
            String url = String.format("%s/query?function=BALANCE_SHEET&symbol=%s&apikey=%s", alphavantageBaseUrl, symbol, alphavantageApiKey);
            Map<String, Object> responseBody = null;
            int attempt = 0;
            boolean hasLongPaused = false;

            while (attempt < MAX_RETRIES) {
                try {
                    responseBody = restTemplate.getForObject(url, Map.class);
                    callCount++;
                    break;
                } catch (ResourceAccessException | HttpServerErrorException e) {

                    attempt++;
                    String errorMsg = String.format("Attempt %d failed for %s: %s%s",
                            attempt, symbol, e.getMessage(),
                            e instanceof HttpServerErrorException ? " (HTTP Status: " + ((HttpServerErrorException) e).getStatusCode() + ")" : "");
                    log.warn(errorMsg);
                    errorLog.add(errorMsg);

                    if (attempt == MAX_RETRIES) {
                        errorLog.add("Max retries reached for " + symbol + ". Skipping.");
                        break;
                    }
                    long pauseDuration = DELAY_MS / 2;

                    if (!hasLongPaused && e instanceof HttpServerErrorException &&
                            ((HttpServerErrorException) e).getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        pauseDuration = 30 * 60 * 1000;
                        log.info("Detected 503 Service Unavailable for {}. Pausing for 30 minutes before retry...", symbol);
                        errorLog.add("Detected 503 for " + symbol + ". Pausing for 30 minutes.");
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
            if (responseBody != null) {
                try {
                    balanceSheetService.processBalanceSheets(symbol, responseBody, errorLog);
                } catch (Exception e) {
                    log.error("Failed to process balance sheets for symbol {}: {}", symbol, e.getMessage());
                    errorLog.add("Failed to process balance sheets for " + symbol + ": " + e.getMessage());
                }
            } else {
                errorLog.add("Failed to fetch balance sheet for symbol: " + symbol + " after " + MAX_RETRIES + " attempts.");
            }
        }
        log.info("Completed fetching balance sheets");
        return balanceSheetRepository.findSymbolsThatHaveStatements(symbols);
    }

    @Override
    public List<String> fetchAndStoreCashFlows(List<String> errorLog, List<String> symbols) {

        List<String> symbolsNeedingUpdate = cashFlowService.getSymbolsNeedingUpdate(symbols);
        log.info("Number of Symbols to update CASH_FLOWS for: {}", symbolsNeedingUpdate.size());
        int callCount = 0;
        log.info("Starting fetching cash flows");

        for (String symbol : symbolsNeedingUpdate) {

            if (callCount >= CALLS_PER_MINUTE) {

                log.info("Hit rate limit (75 calls/minute). Pausing for 1 minute...");

                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    log.error("Interrupted during rate limit delay: {}", e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for symbol " + symbol + ": " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
                callCount = 0;
            }
            String url = String.format("%s/query?function=CASH_FLOW&symbol=%s&apikey=%s", alphavantageBaseUrl, symbol, alphavantageApiKey);
            Map<String, Object> responseBody = null;
            int attempt = 0;
            boolean hasLongPaused = false;

            while (attempt < MAX_RETRIES) {
                try {
                    responseBody = restTemplate.getForObject(url, Map.class);
                    callCount++;
                    break;
                } catch (ResourceAccessException | HttpServerErrorException e) {

                    attempt++;
                    String errorMsg = String.format("Attempt %d failed for %s: %s%s",
                            attempt, symbol, e.getMessage(),
                            e instanceof HttpServerErrorException ? " (HTTP Status: " + ((HttpServerErrorException) e).getStatusCode() + ")" : "");
                    log.warn(errorMsg);
                    errorLog.add(errorMsg);

                    if (attempt == MAX_RETRIES) {
                        errorLog.add("Max retries reached for " + symbol + ". Skipping.");
                        break;
                    }
                    long pauseDuration = DELAY_MS / 2;

                    if (!hasLongPaused && e instanceof HttpServerErrorException &&
                            ((HttpServerErrorException) e).getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        pauseDuration = 30 * 60 * 1000;
                        log.info("Detected 503 Service Unavailable for {}. Pausing for 30 minutes before retry...", symbol);
                        errorLog.add("Detected 503 for " + symbol + ". Pausing for 30 minutes.");
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
            if (responseBody != null) {
                try {
                    cashFlowService.processCashFlows(symbol, responseBody, errorLog);
                } catch (Exception e) {
                    log.error("Failed to process cash flows for symbol {}: {}", symbol, e.getMessage());
                    errorLog.add("Failed to process cash flows for " + symbol + ": " + e.getMessage());
                }
            } else {
                errorLog.add("Failed to fetch cash flow for symbol: " + symbol + " after " + MAX_RETRIES + " attempts.");
            }
        }
        log.info("Completed fetching cash flows");
        return cashFlowService.getSymbolsThatHaveStatements(symbols);
    }

    @Override
    public List<String> fetchAndStoreDividends(List<String> errorLog, List<String> symbols) {

        List<String> symbolsNeedingUpdate = dividendService.getSymbolsNeedingUpdate(symbols);
        log.info("Number of Symbols to update DIVIDENDS for: {}", symbolsNeedingUpdate.size());
        int callCount = 0;
        log.info("Starting fetching dividends");

        for (String symbol : symbolsNeedingUpdate) {
            if (callCount >= CALLS_PER_MINUTE) {
                log.info("Hit rate limit (75 calls/minute). Pausing for 1 minute...");
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    log.error("Interrupted during rate limit delay: {}", e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for symbol " + symbol + ": " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
                callCount = 0;
            }

            String url = String.format("%s/query?function=DIVIDENDS&symbol=%s&apikey=%s", alphavantageBaseUrl, symbol, alphavantageApiKey);
            Map<String, Object> responseBody = null;
            int attempt = 0;
            boolean hasLongPaused = false;

            while (attempt < MAX_RETRIES) {
                try {
                    responseBody = restTemplate.getForObject(url, Map.class);
                    callCount++;
                    break;
                } catch (ResourceAccessException | HttpServerErrorException e) {
                    attempt++;
                    String errorMsg = String.format("Attempt %d failed for %s: %s%s",
                            attempt, symbol, e.getMessage(),
                            e instanceof HttpServerErrorException ? " (HTTP Status: " + ((HttpServerErrorException) e).getStatusCode() + ")" : "");
                    log.warn(errorMsg);
                    errorLog.add(errorMsg);

                    if (attempt == MAX_RETRIES) {
                        errorLog.add("Max retries reached for " + symbol + ". Skipping.");
                        break;
                    }
                    long pauseDuration = DELAY_MS / 2;

                    if (!hasLongPaused && e instanceof HttpServerErrorException &&
                            ((HttpServerErrorException) e).getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        pauseDuration = 30 * 60 * 1000;
                        log.info("Detected 503 Service Unavailable for {}. Pausing for 30 minutes before retry...", symbol);
                        errorLog.add("Detected 503 for " + symbol + ". Pausing for 30 minutes.");
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

            if (responseBody != null) {
                try {
                    dividendService.processDividends(symbol, responseBody, errorLog);
                } catch (Exception e) {
                    log.error("Failed to process dividends for symbol {}: {}", symbol, e.getMessage());
                    errorLog.add("Failed to process dividends for " + symbol + ": " + e.getMessage());
                }
            } else {
                errorLog.add("Failed to fetch dividends for symbol: " + symbol + " after " + MAX_RETRIES + " attempts.");
            }
        }
        log.info("Completed fetching dividends");
        return dividendService.getSymbolsThatHaveDividends(symbols);
    }

    @Override
    public void fetchAndStoreStockPrices(List<String> errorLog, List<String> symbols) {

        List<Asset> assets = assetRepository.findBySymbols(symbols);
        log.info("Fetching stock prices for {} active, tradable assets", assets.size());
        int callCount = 0;

        for (Asset asset : assets) {
            if (callCount >= CALLS_PER_MINUTE) {
                log.info("Hit rate limit (75 calls/minute). Pausing for 1 minute...");
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    log.error("Interrupted during rate limit delay: {}", e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for symbol " + asset.getSymbol() + ": " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
                callCount = 0;
            }

            String url = String.format("%s/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                    alphavantageBaseUrl, asset.getSymbol(), alphavantageApiKey);
            Map<String, Object> responseBody = null;
            int attempt = 0;

            while (attempt < MAX_RETRIES) {
                try {
                    responseBody = restTemplate.getForObject(url, Map.class);
                    callCount++;
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

            if (responseBody != null && responseBody.containsKey("Global Quote")) {
                Map<String, String> quote = (Map<String, String>) responseBody.get("Global Quote");
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
                log.error("Failed to fetch stock price for symbol {}", asset.getSymbol());
                errorLog.add("Failed to fetch stock price for symbol: " + asset.getSymbol());
                asset.setStatus("inactive");
                asset.setLastUpdated(LocalDateTime.now());
                assetRepository.save(asset);
                log.info("Marked asset {} as inactive due to failed price fetch", asset.getSymbol());
            }
        }
        log.info("Completed fetching stock prices");
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

            double adjustedNetIncomeVal = latestIncome.getNetIncome() +
                    latestIncome.getResearchAndDevelopment() +
                    latestCashFlow.getCapitalExpenditures();

            double shares = latestBalance.getCommonStockSharesOutstanding();
            double adjustedEps = (shares != 0) ? adjustedNetIncomeVal / shares : 0.0;

            asset.setAdjustedNetIncome(adjustedNetIncomeVal);
            asset.setAdjustedEarningsPerShare(adjustedEps);
            assetRepository.save(asset);
            log.info("Stored adjusted net income {} and EPS {} for symbol {}", adjustedNetIncomeVal, adjustedEps, asset.getSymbol());
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
        assetRepository.save(asset);
        return price;
    }
}