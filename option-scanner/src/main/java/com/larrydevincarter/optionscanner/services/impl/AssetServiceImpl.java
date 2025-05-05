package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.Asset;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.services.AssetService;
import com.larrydevincarter.optionscanner.services.EarningsService;
import com.larrydevincarter.optionscanner.services.IncomeStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final EarningsService earningsService;
    private final IncomeStatementService incomeStatementService;
    private final RestTemplate restTemplate;

    @Value("${alpaca.api.key}")
    private String alpacaApiKey;
    @Value("${alpaca.api.secret}")
    private String apiSecret;
    @Value("${alpaca.api.base-url}")
    private String AlpcacBaseUrl;

    @Value("${alphavantage.api.key}")
    private String AlphavantageApiKey;
    @Value("${alphavantage.api.base-url}")
    private String AlphavantageBaseUrl;

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

            String url = AlpcacBaseUrl + "/v2/assets?status=active&asset_class=us_equity&attributes=has_options";
            HttpHeaders headers = new HttpHeaders();
            headers.set("APCA-API-KEY-ID", alpacaApiKey);
            headers.set("APCA-API-SECRET-KEY", apiSecret);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            List<Map<String, Object>> assets = restTemplate.exchange(url, HttpMethod.GET, entity, List.class).getBody();

            if (assets != null) {
                for (Map<String, Object> assetData : assets) {
                    Asset asset = new Asset();
                    asset.setId((String) assetData.get("id"));
                    asset.setSymbol((String) assetData.get("symbol"));
                    asset.setName((String) assetData.get("name"));
                    asset.setExchange((String) assetData.get("exchange"));
                    asset.setStatus((String) assetData.get("status"));
                    asset.setTradable((Boolean) assetData.get("tradable"));
                    asset.setLastUpdated(LocalDateTime.now());
                    assetRepository.save(asset);
                }
                log.info("Fetched {} assets", assets.size());
            }
        } catch (Exception e) {
            log.error("Failed to fetch tradable assets: {}", e.getMessage());
            return;
        }
        List<Asset> staleAssets = assetRepository.findActiveStaleAssets(pullStartTime);

        for (Asset staleAsset : staleAssets) {
            try {

                String url = AlpcacBaseUrl + "/v2/assets/" + staleAsset.getId();
                HttpHeaders headers = new HttpHeaders();
                headers.set("APCA-API-KEY-ID", alpacaApiKey);
                headers.set("APCA-API-SECRET-KEY", apiSecret);
                HttpEntity<String> entity = new HttpEntity<>(headers);
                Map<String, Object> assetData = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();

                if (assetData != null) {
                    staleAsset.setStatus((String) assetData.get("status"));
                    staleAsset.setTradable((Boolean) assetData.get("tradable"));
                    staleAsset.setLastUpdated(LocalDateTime.now());
                    assetRepository.save(staleAsset);
                    log.info("Updated stale asset {} to status {}", staleAsset.getSymbol(), staleAsset.getStatus());
                }
            } catch (Exception e) {
                log.error("Failed to update stale asset {}: {}", staleAsset.getSymbol(), e.getMessage());
            }
        }
        log.info("Checked {} stale active assets", staleAssets.size());
        fetchAndStoreIncomeStatements(errorLog);

        try {
            Thread.sleep(DELAY_MS);
        } catch (Exception e) {
            log.error("Interrupted during rate limit delay: {}", e.getMessage());
            errorLog.add("Interrupted during rate limit delay while transitioning from fetching income statement to fetching earnings: " +  e.getMessage());
            Thread.currentThread().interrupt();
        }
        fetchAndStoreEarnings(errorLog);
        writeErrorReport();
    }

    private void writeErrorReport() {

        if (errorLog.isEmpty()) {
            log.info("No errors to report.");
            return;
        }
        String directory = "logs/errors/";
        new File(directory).mkdirs();
        String filename = directory + "error_report_" + LocalDateTime.now().toString().replace(":", "-") + ".txt";

        try (FileWriter writer = new FileWriter(filename)){

            writer.write("Error Report - Option Scanner Revenue Data Fetch\n");
            writer.write("Timestamp: " +LocalDateTime.now() +"\n");
            writer.write("Total Errors: " + errorLog.size() + "\n\n");

            for (String error: errorLog) {
                writer.write(error + "\n");
            }

            log.info("Error report written to {}", filename);
        } catch (IOException e) {
            log.error("Failed to write error report: {}", e.getMessage());
        }
    }

    public void fetchAndStoreIncomeStatements(List<String> errorLog) {

        errorLog.clear();
        List<String> symbols = incomeStatementService.getSymbolsNeedingUpdate();
        log.info("Number of Symbols to update INCOME_STATEMENTS for: {}", symbols.size());
        int callCount = 0;
        log.info("Starting fetching income statements");

        for (String symbol : symbols) {

            if (callCount >= CALLS_PER_MINUTE) {

                log.info("Hit rate limit (75 calls/minute). Pausing for 1 minute...");

                try {
                    Thread.sleep(DELAY_MS);
                } catch (Exception e) {
                    log.error("Interrupted during rate limit delay: {}", e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for symbol " + symbol + ": " +  e.getMessage());
                    Thread.currentThread().interrupt();
                }
                callCount = 0;
            }
            String url = String.format("%s/query?function=INCOME_STATEMENT&symbol=%s&apikey=%s", AlphavantageBaseUrl, symbol, AlphavantageApiKey);
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
    }

    public void fetchAndStoreEarnings(List<String> errorLog) {

        List<String> symbols = earningsService.getSymbolsNeedingUpdate();
        log.info("Fetching earnings for {} symbols with updated income statements", symbols.size());
        int callCount = 0;

        for (String symbol : symbols) {
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
            String url = String.format("%s/query?function=EARNINGS&symbol=%s&apikey=%s", AlphavantageBaseUrl, symbol, AlphavantageApiKey);
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
                    log.error("Failed to process earnings for symbol {}: {}", symbol, e.getMessage(), e);
                    errorLog.add("Failed to process earnings for symbol " + symbol + ": " + e.getMessage());
                }
            } else {
                errorLog.add("Failed to fetch earnings for symbol: " + symbol + " after " + MAX_RETRIES + " attempts.");
            }
        }
        log.info("Completed fetching and storing earnings for all symbols");
    }
}
