package com.larrydevincarter.optionscanner.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TastytradeAuthService {

    private final RestTemplate restTemplate;

    @Value("${tastytrade.api.base-url}")
    private String baseUrl;

    @Value("${tastytrade.oauth.client-id}")
    private String clientId;

    @Value("${tastytrade.oauth.client-secret}")
    private String clientSecret;

    @Value("${tastytrade.oauth.refresh-token}")
    private String refreshToken;

    private String accessToken;
    private LocalDateTime tokenExpiry;

    public String getAccessToken() {
        if (accessToken == null || LocalDateTime.now().isAfter(tokenExpiry.minusMinutes(2))) {
            refreshAccessToken();
        }
        return accessToken;
    }

    private void refreshAccessToken() {
        String url = baseUrl + "/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=refresh_token" +
                "&refresh_token=" + refreshToken +
                "&client_id=" + clientId +
                "&client_secret=" + clientSecret;

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            accessToken = (String) response.getBody().get("access_token");
            tokenExpiry = LocalDateTime.now().plusMinutes(15);
            log.info("Tastytrade access token refreshed");
        } else {
            log.error("Failed to refresh Tastytrade token: {}", response);
            throw new RuntimeException("Token refresh failed");
        }
    }
}