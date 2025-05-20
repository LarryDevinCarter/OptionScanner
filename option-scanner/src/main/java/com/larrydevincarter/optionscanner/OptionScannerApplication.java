package com.larrydevincarter.optionscanner;

import com.larrydevincarter.optionscanner.services.AssetService;
import com.larrydevincarter.optionscanner.services.EarningsService;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.filters.FinancialFilter;
import com.larrydevincarter.optionscanner.services.filters.RevenueGrowthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class OptionScannerApplication {

	@Autowired
	private AssetService assetService;
	@Autowired
	private FilterService filterService;
	@Autowired
	private EarningsService earningsService;
	private final List<String> errorLog = new ArrayList<>();

	public static void main(String[] args) {
		SpringApplication.run(OptionScannerApplication.class, args);
	}

	@Scheduled(fixedRate = 600000000)
	private void callAssetService() {
		assetService.fetchTradableAssets();
	}

}
