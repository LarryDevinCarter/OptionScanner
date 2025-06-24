package com.larrydevincarter.optionscanner;

import com.larrydevincarter.optionscanner.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.services.AssetService;
import com.larrydevincarter.optionscanner.services.EarningsService;
import com.larrydevincarter.optionscanner.services.FilterService;
import com.larrydevincarter.optionscanner.services.filters.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Arrays;
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
	private double revenueCagrThreshold;
	@Value("${revenue.growth.years}")
	private int revenueYears;

	@Value("${eps.growth.cagr.threshold}")
	private double epsCagrThreshold;
	@Value("${eps.growth.years}")
	private int epsYears;

	@Value("${roic.threshold}")
	private double roicThreshold;
	@Value("${roic.years}")
	private int roicYears;
	@Value("${roic.default.tax.rate}")
	private double defaultTaxRate;

	@Value("${debt.to.equity.threshold}")
	private double debtToEquityThreshold;

	public static void main(String[] args) {
		SpringApplication.run(OptionScannerApplication.class, args);
	}

	@Scheduled(fixedRate = 600000000)
	private void startUpTestMethod() {
		FinancialFilter<IncomeStatement> revenueFilter = new RevenueGrowthFilter(revenueCagrThreshold, revenueYears);
		FinancialFilter<Earnings> epsFilter = new EpsGrowthFilter(epsCagrThreshold, epsYears);
		FinancialFilter<Object> roicFilter = new RoicFilter(roicThreshold, roicYears, defaultTaxRate);
		FinancialFilter<BalanceSheet> debtToEquityFilter = new DebtToEquityFilter(debtToEquityThreshold);
		List<FinancialFilter<?>> filters = Arrays.asList(revenueFilter, epsFilter, roicFilter, debtToEquityFilter);
		List<String> filteredSymbols = filterService.getFilteredSymbols(filters);
		log.info("Found {} stocks passing revenue (CAGR > {}% over {} years), EPS (CAGR > {}% over {} years), ROIC (>{}% over {} years), and Debt-to-Equity (<{}%) filters: {}",
				filteredSymbols.size(), revenueCagrThreshold, revenueYears, epsCagrThreshold, epsYears, roicThreshold, roicYears, debtToEquityThreshold, filteredSymbols);
	}

}
