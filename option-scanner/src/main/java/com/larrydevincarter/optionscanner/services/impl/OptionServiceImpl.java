package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.dtos.OwnedAssetDto;
import com.larrydevincarter.optionscanner.models.entities.Asset;
import com.larrydevincarter.optionscanner.models.entities.Option;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.OptionRepository;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.OptionService;
import com.larrydevincarter.optionscanner.services.ReportService;
import com.larrydevincarter.optionscanner.services.TastytradeAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OptionServiceImpl implements OptionService {

    private final OptionRepository optionRepository;
    private final AssetRepository assetRepository;
    private final RestTemplate restTemplate;
    private final FilterService filterService;
    private final ReportService reportService;
    private final TastytradeAuthService tastytradeAuthService;

    @Value("${alpaca.api.key}")
    private String alpacaApiKey;
    @Value("${alpaca.api.secret}")
    private String apiSecret;
    @Value("${alpaca.api.base-url}")
    private String alpacaBaseUrl;
    @Value("${tastytrade.api.base-url}")
    private String tastyBaseUrl;

    private static final int ALPACA_CALLS_PER_MINUTE = 190;
    private static final long DELAY_MS = 60_000;
    private static final int MAX_RETRIES = 3;

    @Override
    public void fetchAndStoreOptionsForSymbol(String symbol, Set<LocalDate> tradingDays) {
        Optional<Asset> optionalAsset = assetRepository.findBySymbol(symbol);
        if (optionalAsset.isEmpty()) {
            log.warn("Asset not found for symbol: {}", symbol);
            return;
        }
        Asset asset = optionalAsset.get();
        Double currentPrice = asset.getCurrentPrice();
        if (currentPrice == null || currentPrice <= 0) {
            log.warn("No valid current price for {} - skipping options fetch", symbol);
            return;
        }

        int holdStreak = filterService.getCurrentHoldStreak();
        double remainingLiquidity = filterService.getCurrentRemainingLiquidity();
        double maxStrike = remainingLiquidity / 100.0;
        double maxDte = 45 + (holdStreak * 7);

        String token = tastytradeAuthService.getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String chainUrl = tastyBaseUrl + "/option-chains/" + symbol + "/nested";
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> chainResponse;
        try {
            chainResponse = restTemplate.exchange(chainUrl, HttpMethod.GET, entity, Map.class);
        } catch (Exception e) {
            log.warn("Exception fetching chain for {}: {}", symbol, e.getMessage());
            return;
        }

        if (!chainResponse.getStatusCode().is2xxSuccessful() || chainResponse.getBody() == null) {
            log.warn("Failed to fetch option chain for {}: status={}", symbol, chainResponse.getStatusCode());
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> body = chainResponse.getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        if (data == null) return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        if (items == null || items.isEmpty()) {
            log.info("No option chain data for {}", symbol);
            return;
        }

        Map<String, Object> chainItem = items.get(0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expirations = (List<Map<String, Object>>) chainItem.get("expirations");
        if (expirations == null || expirations.isEmpty()) {
            log.info("No expirations found in chain for {}", symbol);
            return;
        }

        optionRepository.deleteByUnderlyingSymbolAndOptionType(symbol, "put");

        LocalDate now = LocalDate.now();

        for (Map<String, Object> expGroup : expirations) {
            String expirationStr = (String) expGroup.get("expiration-date");
            if (expirationStr == null) continue;

            LocalDate expiration;
            try {
                expiration = LocalDate.parse(expirationStr);
            } catch (Exception e) {
                log.warn("Invalid expiration date {} for {} - skipping", expirationStr, symbol);
                continue;
            }

            long calendarDte = ChronoUnit.DAYS.between(now, expiration);
            if (calendarDte < 0 || calendarDte > maxDte) {
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> strikes = (List<Map<String, Object>>) expGroup.get("strikes");
            if (strikes == null || strikes.isEmpty()) continue;

            log.info("Strikes " + strikes);
            for (Map<String, Object> strikeGroup : strikes) {
                Object strikeObj = strikeGroup.get("strike-price");
                if (strikeObj == null) continue;

                double strikePrice;
                try {
                    if (strikeObj instanceof Number n) {
                        strikePrice = n.doubleValue();
                    } else if (strikeObj instanceof String s) {
                        strikePrice = Double.parseDouble(s);
                    } else {
                        log.warn("Unexpected strike-price type: {} - skipping", strikeObj.getClass());
                        continue;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid strike-price value '{}' for {} - skipping", strikeObj, symbol);
                    continue;
                }

                if (strikePrice >= currentPrice || strikePrice >= maxStrike) {
                    log.info("StrikePrice: {}, currentPrice: {}, maxPrice {}", strikePrice, currentPrice, maxStrike);
                    continue;
                }

                @SuppressWarnings("unchecked")
                Object putObj = strikeGroup.get("put");
                if (putObj == null) continue;

                if (!(putObj instanceof String)) {
                    log.warn("Unexpected type for 'put' field (expected String): {} - skipping strike {}",
                            putObj.getClass().getName(), strikePrice);
                    continue;
                }

                String putSymbol = (String) putObj;
                if (putSymbol.isBlank()) continue;

                String streamerSymbol = (String) strikeGroup.get("put-streamer-symbol");
                if (streamerSymbol == null || streamerSymbol.isBlank()) {
                    log.warn("Missing put-streamer-symbol for put {} at strike {} - skipping", putSymbol, strikePrice);
                    continue;
                }

                Option option = new Option();
                option.setSymbol(putSymbol);
                option.setUnderlyingSymbol(symbol);
                option.setExpirationDate(expiration);
                option.setStrike(strikePrice);
                option.setOptionType("put");

                log.info("Option before fetchQ&G " + option);
                fetchQuoteAndGreeks(option, token);
                log.info("Option after fetchQ&G " + option);
                Double adjustedEps = asset.getAdjustedEarningsPerShare();
                if (adjustedEps != null && adjustedEps != 0) {
                    option.setAdjustedPe(strikePrice / adjustedEps);
                }
                log.info("Option after adjustedEPS " + option);

                int tradingDaysRemaining = calculateTradingDays(now, expiration, tradingDays);
                if (option.getPreviousClose() != null && strikePrice > 0 && tradingDaysRemaining > 0) {
                    double adjustedPremium = option.getPreviousClose() - 0.01;
                    double dailyYield = (adjustedPremium / strikePrice) * 100.0 / tradingDaysRemaining;
                    option.setYield(dailyYield);
                }

                log.info("Option after yield " + option);
                option.setLastUpdated(LocalDateTime.now());
                option.setId(UUID.randomUUID().toString());
                optionRepository.save(option);
            }
        }

        int storedCount = optionRepository.findByUnderlyingSymbolAndOptionTypeOrderByYieldDesc(symbol, "put").size();
        log.info("Stored {} put options for {}", storedCount, symbol);
    }

    private void fetchQuoteAndGreeks(Option option, String token) {
        String occSymbol = option.getSymbol();  // Use OCC symbol from option (e.g., "TSLA 260209P00347500")
        String quoteUrl = tastyBaseUrl + "/market-data/by-type?equity-option=" + occSymbol;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> quoteResp = restTemplate.exchange(quoteUrl, HttpMethod.GET, entity, Map.class);
            if (!quoteResp.getStatusCode().is2xxSuccessful() || quoteResp.getBody() == null) {
                log.warn("Failed to fetch quote for {}: status={}", occSymbol, quoteResp.getStatusCode());
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = quoteResp.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null || data.isEmpty()) {
                log.debug("No data in quote response for {}", occSymbol);
                return;
            }

            // Response structure: data → items → [0] → {quote fields + "greeks": {map}}
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
            if (items == null || items.isEmpty()) {
                log.debug("No items in quote data for {}", occSymbol);
                return;
            }

            Map<String, Object> quoteData = items.get(0);  // Single symbol → first (only) item
            if (quoteData == null) return;

            // Parse last-price (use "last-price" or fallback to mark/mid)
            Object lastPriceObj = quoteData.get("last");
            double lastPrice = parseDouble(lastPriceObj, occSymbol, "last");
            option.setPreviousClose(lastPrice);  // Or use "close-price" if you mean prior session close

            // Parse greeks map
            option.setDelta(parseDouble(quoteData.get("delta"), occSymbol, "delta"));
            // Add others as needed: gamma, theta, vega, rho, implied-volatility, etc.
            // e.g., option.setGamma(parseDouble(greeks.get("gamma"), occSymbol, "gamma"));

        } catch (Exception e) {
            log.warn("Failed to fetch quote/Greeks for {}: {}", occSymbol, e.getMessage());
        }
    }

    // Helper to parse potential String/Double/Number
    private double parseDouble(Object value, String symbol, String field) {
        if (value == null) return 0.0;
        try {
            if (value instanceof Number n) {
                return n.doubleValue();
            } else if (value instanceof String s) {
                return Double.parseDouble(s);
            } else {
                log.warn("Unexpected type for {} '{}' in {}: {}", field, value, symbol, value.getClass().getName());
                return 0.0;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid format for {} '{}' in {}: {}", field, value, symbol, e.getMessage());
            return 0.0;
        }
    }

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
    public void fetchCoveredCallOptions(OwnedAssetDto dto) {
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