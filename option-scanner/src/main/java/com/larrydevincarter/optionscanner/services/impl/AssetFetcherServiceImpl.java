package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.Asset;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.services.AssetService;
import org.hibernate.type.descriptor.jdbc.ObjectNullResolvingJdbcType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AssetFetcherServiceImpl implements AssetService {

    private static final Logger logger = LoggerFactory.getLogger(AssetFetcherServiceImpl.class);

    @Autowired
    private AssetRepository assetRepository;
    @Value("${alpaca.api.key}")
    private String apiKey;
    @Value("${alpaca.api.secret}")
    private String apiSecret;
    @Value("${alpaca.api.base-url}")
    private String baseUrl;

    private  final RestTemplate restTemplate = new RestTemplate();

    @Scheduled(cron = "0 0 2 * * ?")
    @Override
    public void fetchTradableAssets() {

        logger.info("Starting fetching tradable assets");

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
                logger.info("Fetched {} assets", assets.size());
            }
        } catch (Exception e) {
            logger.error("Failed to fetch tradable assets: {}", e.getMessage());
        }
    }
}
