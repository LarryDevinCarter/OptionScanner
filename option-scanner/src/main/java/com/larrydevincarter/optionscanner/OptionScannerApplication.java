package com.larrydevincarter.optionscanner;

import com.larrydevincarter.optionscanner.services.AssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
public class OptionScannerApplication {

	@Autowired
	private AssetService assetService;

	public static void main(String[] args) {
		SpringApplication.run(OptionScannerApplication.class, args);
	}

	@Scheduled(fixedRate = 60000)
	private void callAssetService() {
		assetService.fetchTradableAssets();
	}
}