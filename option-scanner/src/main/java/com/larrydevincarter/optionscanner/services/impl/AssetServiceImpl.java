package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.Asset;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.services.AssetService;
import com.larrydevincarter.optionscanner.services.IncomeStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final IncomeStatementService incomeStatementService;
    private final RestTemplate restTemplate;

    @Value("${alpaca.api.key}")
    private String apiKey;
    @Value("${alpaca.api.secret}")
    private String apiSecret;
    @Value("${alpaca.api.base-url}")
    private String baseUrl;

    @Scheduled(cron = "0 0 2 * * ?", zone = "America/Chicago")
    @Override
    public void fetchTradableAssets() {

        LocalDateTime pullStartTime = LocalDateTime.now();
        log.info("Starting fetching tradable assets");

        try {

            String url = baseUrl + "/v2/assets?status=active&asset_class=us_equity&attributes=has_options";
            HttpHeaders headers = new HttpHeaders();
            headers.set("APCA-API-KEY-ID", apiKey);
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

                String url = baseUrl + "/v2/assets/" + staleAsset.getId();
                HttpHeaders headers = new HttpHeaders();
                headers.set("APCA-API-KEY-ID", apiKey);
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
        incomeStatementService.fetchAndStoreIncomeStatements();
    }

}
