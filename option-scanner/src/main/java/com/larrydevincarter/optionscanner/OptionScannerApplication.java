package com.larrydevincarter.optionscanner;

import com.larrydevincarter.optionscanner.services.AssetService;
import com.larrydevincarter.optionscanner.services.EarningsService;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.filters.EpsGrowthFilter;
import com.larrydevincarter.optionscanner.services.filters.FinancialFilter;
import com.larrydevincarter.optionscanner.services.filters.RevenueGrowthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class OptionScannerApplication {

	@Autowired
	private AssetService assetService;
	@Autowired
	private FilterService filterService;
	@Autowired
	private EarningsService earningsService;
	private final List<String> errorLog = new ArrayList<>();

	@Value("${revenue.growth.cagr.threshold}")
	private double defaultRevenueCagrThreshold;
	@Value("${revenue.growth.years}")
	private int defaultRevenueYears;
	@Value("${eps.growth.cagr.threshold}")
	private double defaultEpsCagrThreshold;
	@Value("${eps.growth.years}")
	private int defaultEpsYears;

	public static void main(String[] args) {
		SpringApplication.run(OptionScannerApplication.class, args);
	}

	@Scheduled(fixedRate = 600000000)
	private void startUpTestMethod() {
		try {
			log.info("Starting filter test with default criteria: Revenue CAGR > {}% over {} years, EPS CAGR > {}% over {} years",
					defaultRevenueCagrThreshold, defaultRevenueYears, defaultEpsCagrThreshold, defaultEpsYears);

			// Create list of filters with default criteria
			List<FinancialFilter> filters = new ArrayList<>();
			filters.add(new RevenueGrowthFilter(defaultRevenueCagrThreshold, defaultRevenueYears));
			filters.add(new EpsGrowthFilter(defaultEpsCagrThreshold, defaultEpsYears));

			// Get filtered symbols
			List<String> filteredSymbols = filterService.getFilteredSymbols(filters);

			// Log results
			if (filteredSymbols.isEmpty()) {
				log.info("No symbols meet the default filter criteria.");
			} else {
				log.info("Found {} symbols meeting default filter criteria: {}", filteredSymbols.size(), filteredSymbols);
			}
		} catch (Exception e) {
			String errorMsg = "Error applying filters in startUpTestMethod: " + e.getMessage();
			log.error(errorMsg, e);
			errorLog.add(errorMsg);
		}
	}

}
