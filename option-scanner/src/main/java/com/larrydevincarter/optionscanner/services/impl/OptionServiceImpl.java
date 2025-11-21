package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.dtos.OwnedAssetDTO;
import com.larrydevincarter.optionscanner.models.entities.Asset;
import com.larrydevincarter.optionscanner.models.entities.Option;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.OptionRepository;
import com.larrydevincarter.optionscanner.services.OptionService;
import com.larrydevincarter.optionscanner.services.ReportService;
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
    private final ReportService reportService;

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

    @Override
    @Transactional
    public void fetchCoveredCallOptions(OwnedAssetDTO dto) {
        String symbol = dto.getSymbol().toUpperCase();
        Double dca = dto.getDollarCostAverage();

        Optional<Asset> assetOpt = assetRepository.findBySymbol(symbol);
        if (assetOpt.isEmpty()) {
            log.warn("Cannot fetch covered calls – asset not found in database: {}", symbol);
            return;
        }
        Asset asset = assetOpt.get();

        Double currentPrice = asset.getCurrentPrice();
        if (currentPrice == null) {
            log.warn("Cannot fetch covered calls for {} – current price is null", symbol);
            return;
        }

        // Delete old covered calls for this underlying (we only keep fresh data)
        optionRepository.deleteByUnderlyingSymbolAndOptionType(symbol, "call");

        LocalDate today = LocalDate.now();

        // We'll reuse the same trading-day logic you already have
        Set<LocalDate> tradingDays = fetchTradingCalendar(); // helper below
        LocalDate previousTradingDay = tradingDays.stream()
                .filter(d -> d.isBefore(today))
                .max(Comparator.naturalOrder())
                .orElse(today.minusDays(1));

        List<Option> callsToSave = new ArrayList<>();
        String pageToken = null;
        String baseUrl = alpacaBaseUrl + "/v2/options/contracts"
                + "?underlying_symbols=" + symbol
                + "&type=call"
                + "&expiration_date_gte=" + today
                + "&strike_price_gte=" + String.format("%.2f", dca)
                + "&limit=1000";

        HttpHeaders headers = new HttpHeaders();
        headers.set("APCA-API-KEY-ID", alpacaApiKey);
        headers.set("APCA-API-SECRET-KEY", apiSecret);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        do {
            String url = baseUrl + (pageToken != null ? "&page_token=" + pageToken : "");
            Map<String, Object> response = null;

            try {
                response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();
            } catch (Exception e) {
                log.error("Failed to fetch call contracts page for {}: {}", symbol, e.getMessage());
                break;
            }

            if (response == null) break;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contracts = (List<Map<String, Object>>) response.get("option_contracts");
            if (contracts == null || contracts.isEmpty()) break;

            for (Map<String, Object> contract : contracts) {
                String closePriceStr = (String) contract.get("close_price");
                String closeDateStr = (String) contract.get("close_price_date");

                Double closePrice = (closePriceStr != null && !closePriceStr.isEmpty())
                        ? Double.parseDouble(closePriceStr) : null;
                LocalDate closeDate = (closeDateStr != null && !closeDateStr.isEmpty())
                        ? LocalDate.parse(closeDateStr) : null;

                boolean tradedPreviousDay = closeDate != null
                        && closeDate.equals(previousTradingDay)
                        && closePrice != null && closePrice > 0;

                if (!tradedPreviousDay) continue;

                Option call = new Option();
                call.setId((String) contract.get("id"));
                call.setSymbol((String) contract.get("symbol"));
                call.setUnderlyingSymbol(symbol);
                call.setExpirationDate(LocalDate.parse((String) contract.get("expiration_date")));
                call.setStrike(Double.valueOf((String) contract.get("strike_price")));
                call.setOptionType("call");
                call.setPreviousClose(closePrice);
                call.setTradedPreviousDay(true);

                // Yield calculation identical to puts (annualized premium yield)
                int tradingDaysRemaining = calculateTradingDays(today, call.getExpirationDate(), tradingDays);
                if (tradingDaysRemaining > 0 && closePrice > 0.01) {
                    double rawYield = ((closePrice - 0.01) / dto.getDollarCostAverage() / tradingDaysRemaining) * 100;
                    call.setYield(Math.round(rawYield * 1000.0) / 1000.0);
                }

                call.setLastUpdated(LocalDateTime.now());
                callsToSave.add(call);
            }

            pageToken = (String) response.get("next_page_token");
        } while (pageToken != null);

        if (!callsToSave.isEmpty()) {
            optionRepository.saveAll(callsToSave);
            log.info("Stored {} covered call options for owned asset {}", callsToSave.size(), symbol);
            reportService.generateCoveredCallsReport(symbol, dca);
        } else {
            log.info("No qualifying covered call options found for {}", symbol);
        }
    }

    // Helper – extract duplicate calendar fetch into a reusable private method
    private Set<LocalDate> fetchTradingCalendar() {
        Set<LocalDate> days = new HashSet<>();
        try {
            String url = alpacaBaseUrl + "/v2/calendar?start=" + LocalDate.now().minusDays(30)
                    + "&end=" + LocalDate.now().plusYears(4);
            HttpHeaders headers = new HttpHeaders();
            headers.set("APCA-API-KEY-ID", alpacaApiKey);
            headers.set("APCA-API-SECRET-KEY", apiSecret);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            List<Map<String, Object>> resp = restTemplate.exchange(url, HttpMethod.GET, entity, List.class).getBody();
            if (resp != null) {
                for (Map<String, Object> d : resp) {
                    days.add(LocalDate.parse((String) d.get("date")));
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch trading calendar for covered calls: {}", e.getMessage());
        }
        return days;
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