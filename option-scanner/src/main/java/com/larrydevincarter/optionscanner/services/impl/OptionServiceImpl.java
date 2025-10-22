package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.entities.Asset;
import com.larrydevincarter.optionscanner.models.entities.Option;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.OptionRepository;
import com.larrydevincarter.optionscanner.services.OptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OptionServiceImpl implements OptionService {

    private final OptionRepository optionRepository;
    private final AssetRepository assetRepository;
    private final RestTemplate restTemplate;

    @Value("${alpaca.api.key}")
    private String alpacaApiKey;
    @Value("${alpaca.api.secret}")
    private String apiSecret;
    @Value("${alpaca.api.base-url}")
    private String alpacaBaseUrl;

    private static final int ALPACA_CALLS_PER_MINUTE = 190;
    private static final long DELAY_MS = 60_000;
    private static final int MAX_RETRIES = 3;

    @Override
    @Transactional
    public void processOptionsForSymbol(String symbol, List<String> errorLog, Set<LocalDate> tradingDays, LocalDate previousTradingDay) {
        Optional<Asset> optionalAsset = assetRepository.findBySymbol(symbol);
        if (optionalAsset.isEmpty()) {
            log.warn("Asset not found for symbol: {}", symbol);
            errorLog.add("Asset not found for symbol: " + symbol);
            return;
        }
        Asset asset = optionalAsset.get();

        try {
            optionRepository.deleteByUnderlyingSymbol(symbol);
            log.debug("Deleted existing options for symbol: {}", symbol);

            Double currentPrice = asset.getCurrentPrice();
            if (currentPrice == null) {
                log.warn("Skipping options fetch for {} due to null currentPrice", symbol);
                errorLog.add("Skipping options for " + symbol + ": null currentPrice");
                return;
            }

            List<Map<String, Object>> allContracts = new ArrayList<>();
            String pageToken = null;
            String baseContractsUrl = alpacaBaseUrl + "/v2/options/contracts?underlying_symbols=" + symbol + "&expiration_date_gte=" + LocalDate.now().toString() +
                    "&type=put&strike_price_lte=" + currentPrice + "&limit=10000";
            log.info(baseContractsUrl);
            HttpHeaders headers = new HttpHeaders();
            headers.set("APCA-API-KEY-ID", alpacaApiKey);
            headers.set("APCA-API-SECRET-KEY", apiSecret);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            do {
                String contractsUrl = baseContractsUrl + (pageToken != null ? "&page_token=" + pageToken : "");
                Map<String, Object> contractsResponse = null;
                int attempt = 0;
                while (attempt < MAX_RETRIES) {
                    try {
                        contractsResponse = restTemplate.exchange(contractsUrl, HttpMethod.GET, entity, Map.class).getBody();
                        break;
                    } catch (HttpClientErrorException e) {
                        if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                            String errorMsg = String.format("Permanent error (422) for %s: %s. Skipping.", symbol, e.getMessage());
                            log.warn(errorMsg);
                            errorLog.add(errorMsg);
                            contractsResponse = null;
                            attempt = MAX_RETRIES;
                            break;
                        } else {
                            attempt++;
                            String errorMsg = String.format("Attempt %d failed to fetch contracts page for %s: %s", attempt, symbol, e.getMessage());
                            log.warn(errorMsg);
                            errorLog.add(errorMsg);
                            if (attempt == MAX_RETRIES) break;
                            try {
                                Thread.sleep(DELAY_MS / 2);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        attempt++;
                        String errorMsg = String.format("Attempt %d failed to fetch contracts page for %s: %s", attempt, symbol, e.getMessage());
                        log.warn(errorMsg);
                        errorLog.add(errorMsg);
                        if (attempt == MAX_RETRIES) break;
                        try {
                            Thread.sleep(DELAY_MS / 2);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                if (contractsResponse == null) break;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contracts = (List<Map<String, Object>>) contractsResponse.get("option_contracts");
                if (contracts != null) {
                    allContracts.addAll(contracts);
                }
                pageToken = (String) contractsResponse.get("next_page_token");
            } while (pageToken != null);

            if (allContracts.isEmpty()) {
                log.info("No option contracts found for symbol: {}", symbol);
                return;
            }

            List<Option> optionsToSave = new ArrayList<>();
            for (Map<String, Object> contract : allContracts) {
                String closePriceStr = (String) contract.get("close_price");
                Double previousClose = closePriceStr != null && !closePriceStr.isEmpty() ? Double.valueOf(closePriceStr) : null;

                String closeDateStr = (String) contract.get("close_price_date");
                LocalDate closeDate = closeDateStr != null && !closeDateStr.isEmpty() ? LocalDate.parse(closeDateStr) : null;

                boolean tradedPreviousDay = (closeDate != null && closeDate.equals(previousTradingDay) && previousClose != null && previousClose > 0);

                if (!tradedPreviousDay) {
                    continue;
                }

                Option option = new Option();
                option.setId((String) contract.get("id"));
                option.setSymbol((String) contract.get("symbol"));
                option.setUnderlyingSymbol((String) contract.get("underlying_symbol"));
                option.setExpirationDate(LocalDate.parse((String) contract.get("expiration_date")));
                option.setStrike(Double.valueOf((String) contract.get("strike_price")));
                option.setOptionType("put");
                option.setPreviousClose(previousClose);
                option.setTradedPreviousDay(tradedPreviousDay);

                Double adjustedEps = asset.getAdjustedEarningsPerShare();
                if (adjustedEps != null && adjustedEps != 0) {
                    option.setAdjustedPe(option.getStrike() / adjustedEps);
                }

                int tradingDaysRemaining = calculateTradingDays(LocalDate.now(), option.getExpirationDate(), tradingDays);
                if (tradingDaysRemaining > 0 && previousClose > 0.01) {
                    double yieldVal = ((previousClose - 0.01) / option.getStrike() / tradingDaysRemaining) * 100;
                    option.setYield(Math.round(yieldVal * 1000.0) / 1000.0);
                }

                option.setLastUpdated(LocalDateTime.now());
                optionsToSave.add(option);
            }

            optionRepository.saveAll(optionsToSave);
            log.info("Stored {} put options for symbol: {}", optionsToSave.size(), symbol);

        } catch (Exception e) {
            log.error("Failed to process options for symbol {}: {}", symbol, e.getMessage());
            errorLog.add("Failed to process options for " + symbol + ": " + e.getMessage());
        }
    }

    private int calculateTradingDays(LocalDate start, LocalDate end, Set<LocalDate> tradingDays) {
        if (start.isAfter(end)) return 0;
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (tradingDays.isEmpty()) {
                DayOfWeek day = current.getDayOfWeek();
                if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                    count++;
                }
            } else if (tradingDays.contains(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }
}