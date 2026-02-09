package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.dtos.MarketStatusDto;
import com.larrydevincarter.optionscanner.services.MarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketServiceImpl implements MarketService {

    private final RestTemplate restTemplate;

    @Value("${alpaca.api.base-url}")
    private String alpacaBaseUrl;

    @Value("${alpaca.api.key}")
    private String alpacaApiKey;

    @Value("${alpaca.api.secret}")
    private String apiSecret;

    @Override
    public MarketStatusDto getMarketStatus() {
        LocalDate today = LocalDate.now();
        String url = alpacaBaseUrl + "/v2/calendar?start=" + today + "&end=" + today;

        HttpHeaders headers = new HttpHeaders();
        headers.set("APCA-API-KEY-ID", alpacaApiKey);
        headers.set("APCA-API-SECRET-KEY", apiSecret);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class).getBody();

            if (response != null && !response.isEmpty()) {
                Map<String, Object> day = response.get(0);
                String closeStr = (String) day.get("close");
                LocalTime closeET = LocalTime.parse(closeStr);

                ZonedDateTime closeETZoned = ZonedDateTime.of(today, closeET, ZoneId.of("America/New_York"));
                ZonedDateTime closeCTZoned = closeETZoned.withZoneSameInstant(ZoneId.of("America/Chicago"));
                String closeTimeCT = closeCTZoned.toLocalTime().toString();

                return new MarketStatusDto(true, closeTimeCT);
            } else {
                return new MarketStatusDto(false, null);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch market status from Alpaca calendar: {}", e.getMessage());
            throw new RuntimeException("Unable to retrieve market calendar from Alpaca", e);
        }
    }

    @Override
    public Set<LocalDate> getTradingDays() {
        LocalDate today = LocalDate.now();
        LocalDate calendarStart = today.minusDays(30);
        LocalDate maxEnd = today.plusYears(5);
        Set<LocalDate> tradingDays = new HashSet<>();
        try {
            String calendarUrl = alpacaBaseUrl + "/v2/calendar?start=" + calendarStart.toString() + "&end=" + maxEnd.toString();
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
}