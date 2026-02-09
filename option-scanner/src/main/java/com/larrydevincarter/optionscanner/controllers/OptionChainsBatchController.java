package com.larrydevincarter.optionscanner.controllers;

import com.larrydevincarter.optionscanner.models.dtos.OptionBatchRequestDto;
import com.larrydevincarter.optionscanner.models.dtos.OptionBatchResponseDto;
import com.larrydevincarter.optionscanner.models.entities.Asset;
import com.larrydevincarter.optionscanner.models.entities.Option;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.OptionRepository;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.MarketService;
import com.larrydevincarter.optionscanner.services.OptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class OptionChainsBatchController {

    private final AssetRepository assetRepository;
    private final OptionRepository optionRepository;
    private final FilterService filterService;
    private final MarketService marketService;
    private final OptionService optionService;
    @PostMapping("/option-chains/batch")
    public OptionBatchResponseDto getBatchOptionChains(@RequestBody List<OptionBatchRequestDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new OptionBatchResponseDto(List.of(), Map.of());
        }

        List<String> tickers = dtos.stream()
                .map(OptionBatchRequestDto::getTicker)
                .distinct()
                .collect(Collectors.toList());

        List<Asset> assets = assetRepository.findBySymbols(tickers);

        Map<String, Asset> assetByTicker = assets.stream()
                .collect(Collectors.toMap(Asset::getSymbol, a -> a));

        for (OptionBatchRequestDto dto : dtos) {
            String ticker = dto.getTicker();
            Double requestedPrice = dto.getCurrentPrice();

            if (requestedPrice == null || requestedPrice <= 0) {
                log.warn("Invalid currentPrice provided for {} - skipping update", ticker);
                continue;
            }

            Asset asset = assetByTicker.get(ticker);
            if (asset != null) {
                asset.setCurrentPrice(requestedPrice);
                asset.setLastPriceUpdated(LocalDateTime.now());
                assetRepository.save(asset);
                log.debug("Updated currentPrice for {} to {}", ticker, requestedPrice);
            } else {
                log.warn("No existing Asset found for {} - cannot update price", ticker);
            }
        }

        for (String ticker : tickers) {
            optionService.fetchAndStoreOptionsForSymbol(ticker, marketService.getTradingDays()); // adjust as needed
        }

        int holdStreak = filterService.getCurrentHoldStreak();
        double deltaThreshold = -0.3 - (0.01 * holdStreak);
        long dteThreshold = 45 + (7L * holdStreak);
        double dailyYieldThreshold = 0.23 - (0.01 * holdStreak);

        Map<String, List<Option>> optionChains = new HashMap<>();

        LocalDate now = LocalDate.now();

        for (String ticker : tickers) {
            Asset asset = assetByTicker.get(ticker);
            if (asset == null) continue;

            Double currentPrice = asset.getCurrentPrice();
            if (currentPrice == null) continue;

            List<Option> puts = optionRepository.findByUnderlyingSymbolAndOptionTypeOrderByYieldDesc(
                    ticker, "put");

            List<Option> filtered = puts.stream()
                    .filter(opt -> {
                        long dte = ChronoUnit.DAYS.between(now, opt.getExpirationDate());
                        if (dte < 0) return false;

                        return opt.getStrike() < currentPrice &&
                                opt.getDelta() != null && opt.getDelta() >= deltaThreshold &&
                                dte <= dteThreshold &&
                                opt.getYield() != null && opt.getYield() >= dailyYieldThreshold;
                    })
                    .collect(Collectors.toList());

            if (!filtered.isEmpty()) {
                optionChains.put(ticker, filtered);
            }
        }

        return new OptionBatchResponseDto(assets, optionChains);
    }
}