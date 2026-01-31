package com.larrydevincarter.optionscanner.controllers;

import com.larrydevincarter.optionscanner.models.dtos.MarketStatusDto;
import com.larrydevincarter.optionscanner.services.MarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class MarketController {

    private final MarketService marketService;

    @GetMapping("/market-status")
    public ResponseEntity<MarketStatusDto> getMarketStatus() {
        try {
            MarketStatusDto status = marketService.getMarketStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Market status endpoint failed (Alpaca unreachable or error): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new MarketStatusDto(false, null));
        }
    }
}