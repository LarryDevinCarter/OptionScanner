package com.larrydevincarter.optionscanner;

import com.larrydevincarter.optionscanner.models.dtos.SoldOptionDto;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.services.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
	@Autowired
	private IncomeStatementService incomeStatementService;
	@Autowired
	private BalanceSheetService balanceSheetService;
	@Autowired
	private CashFlowService cashFlowService;
	@Autowired
	private DividendService dividendService;
	@Autowired
	private ReportService reportService;
	@Autowired
	private AssetRepository assetRepository;
	@Autowired
	private OptionService optionService;
	@Autowired
	private TastytradeAuthService tastytradeAuthService;
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

	@Value("${fcf.yield.threshold:4.0}")
	private double defaultFcfYieldThreshold;

	@Value("${operating.margin.threshold}")
	private double operatingMarginThreshold;
	@Value("${operating.margin.years}")
	private int operatingMarginYears;

	public static void main(String[] args) {
		SpringApplication.run(OptionScannerApplication.class, args);
	}

	@Scheduled(fixedRate = 600000000)
	private void startUpTestMethod() {
//		List<String> symbols = assetRepository.findActiveTradableSymbols();
//		System.out.println(symbols.size() + " active and tradable symbols.");
//		List<String> symbolsNeedingUpdated = incomeStatementService.getSymbolsNeedingUpdate(symbols);
//		System.out.println(symbolsNeedingUpdated.size() + " incomeStatements being updated.");
//		symbols = incomeStatementService.getSymbolsThatHaveStatements(symbols);
//		System.out.println(symbols.size() + " symbols with incomeStatements being passed to the next round.");
//
//		symbolsNeedingUpdated = earningsService.getSymbolsNeedingUpdate(symbols);
//		System.out.println(symbolsNeedingUpdated.size() + " earnings being updated.");
//		symbols = earningsService.getSymbolsThatHaveStatements(symbols);
//		System.out.println(symbols.size() + " symbols with earnings being passed to the next round.");
//
//		symbolsNeedingUpdated = balanceSheetService.getSymbolsNeedingUpdate(symbols);
//		System.out.println(symbolsNeedingUpdated.size() + " balanceSheets being updated.");
//		symbols = balanceSheetService.getSymbolsThatHaveStatements(symbols);
//		System.out.println(symbols.size() + " symbols with balanceSheets being passed to the next round.");
//
//		symbolsNeedingUpdated = cashFlowService.getSymbolsNeedingUpdate(symbols);
//		System.out.println(symbolsNeedingUpdated.size() + " cashFlows being updated.");
//		symbols = cashFlowService.getSymbolsThatHaveStatements(symbols);
//		System.out.println(symbols.size() + " symbols with cashFlow being passed to the next round.");
//
//		symbolsNeedingUpdated = dividendService.getSymbolsNeedingUpdate(symbols);
//		System.out.println(symbolsNeedingUpdated.size() + " dividends being updated.");
//		symbols = dividendService.getSymbolsThatHaveDividends(symbols);
//		System.out.println(symbols.size() + " symbols with dividends being passed to the next round.");

//		LocalDateTime startTime = LocalDateTime.now();
//		assetService.fetchTradableAssets();
//		LocalDateTime endTime = LocalDateTime.now();
//		Duration duration = Duration.between(startTime, endTime);
//		long hours = duration.toHours();
//		long minutes = duration.toMinutesPart();
//		long seconds = duration.toSecondsPart();
//		long millis = duration.toMillisPart();
//
//		System.out.println("fetchTradableAssets took " + hours + "h " + minutes + "m " + seconds + "s " + millis + "ms");


//		List<FinancialFilter> filters = List.of(
//				new RevenueGrowthFilter(revenueCagrThreshold,revenueYears),
//				new EpsGrowthFilter(epsCagrThreshold,epsYears),
//				new RoicFilter(roicThreshold, roicYears, defaultTaxRate),
//				new DebtToEquityFilter(debtToEquityThreshold),
//				new FreeCashFlowYieldFilter(defaultFcfYieldThreshold),
//				new OperatingMarginFilter(operatingMarginThreshold, operatingMarginYears)
//		);
//		List<String> symbols2 = filterService.getFilteredSymbols(filters);
//		System.out.println(symbols2.size() + " symbols meet filers.");
//		System.out.println(symbols2);

//		symbols = incomeStatementService.getSymbolsThatHaveStatements(symbols);
//		symbols = earningsService.getSymbolsThatHaveStatements(symbols);
//		symbols = balanceSheetService.getSymbolsThatHaveStatements(symbols);
//		symbols = cashFlowService.getSymbolsThatHaveStatements(symbols);


//		List<String> symbols = new ArrayList<>(List.of("IGT", "TSLA", "TSM"));
//		assetService.fetchAndStoreStockPrices(errorLog, symbols);
//		assetService.fetchAndStoreOptions(errorLog, symbols);
//		assetService.writeErrorReport();

//		Double maxStrike = 19.0;
//		reportService.generateReport(maxStrike);
//
//		maxStrike = 445.00;
//		reportService.generateReport(maxStrike);

		List<SoldOptionDto> sampleDtos = List.of(
				new SoldOptionDto() {{
					setUnderlyingSymbol("UMC");
					setStrikePrice(11.00);
					setExpirationDate(LocalDate.of(2026, 2, 20));
					setOptionType("put");
					setSoldDate(LocalDate.of(2026, 1, 28));
					setSoldPrice(0.75);
				}},
//				new SoldOptionDTO() {{
//					setUnderlyingSymbol("LITS");
//					setStrikePrice(2.50);
//					setExpirationDate(LocalDate.of(2025, 12, 19));
//					setOptionType("put");
//					setSoldDate(LocalDate.of(2025, 8, 20));
//					setSoldPrice(0.20);
//				}},
//				new SoldOptionDTO() {{
//					setUnderlyingSymbol("DCGO");
//					setStrikePrice(1.50);
//					setExpirationDate(LocalDate.of(2025, 11, 21));
//					setOptionType("put");
//					setSoldDate(LocalDate.of(2025, 9, 5));
//					setSoldPrice(0.10);
//				}},
				new SoldOptionDto() {{
					setUnderlyingSymbol("NPWR");
					setStrikePrice(1.00);
					setExpirationDate(LocalDate.of(2027, 1, 15));
					setOptionType("put");
					setSoldDate(LocalDate.of(2025, 9, 19));
					setSoldPrice(0.30);
//				}},
//				new SoldOptionDTO() {{
//					setUnderlyingSymbol("CRNT");
//					setStrikePrice(2.00);
//					setExpirationDate(LocalDate.of(2026, 1, 16));
//					setOptionType("put");
//					setSoldDate(LocalDate.of(2025, 9, 19));
//					setSoldPrice(0.10);
				}}
		);
		reportService.generateSoldOptionsReports(sampleDtos);

//		OwnedAssetDTO dto = new OwnedAssetDTO() {{
//			setSymbol("UMC");
//			setDollarCostAverage(12.00);
//		}};
//		optionService.fetchCoveredCallOptions(dto);

//		tastytradeAuthService.refreshAccessToken();
	}

}