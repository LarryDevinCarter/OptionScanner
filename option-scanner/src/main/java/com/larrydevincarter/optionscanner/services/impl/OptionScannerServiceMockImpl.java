package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.CallOpportunity;
import com.larrydevincarter.optionscanner.entities.Portfolio;
import com.larrydevincarter.optionscanner.entities.PutOpportunity;
import com.larrydevincarter.optionscanner.entities.StockOverview;
import com.larrydevincarter.optionscanner.repositories.CallOpportunityRepository;
import com.larrydevincarter.optionscanner.repositories.PortfolioRepository;
import com.larrydevincarter.optionscanner.repositories.PutOpportunityRepository;
import com.larrydevincarter.optionscanner.repositories.StockOverviewRepository;
import com.larrydevincarter.optionscanner.services.OptionScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("mock")
public class OptionScannerServiceMockImpl implements OptionScannerService {

    private static final Logger logger = LoggerFactory.getLogger(OptionScannerServiceMockImpl.class);

    @Autowired
    private PutOpportunityRepository putRepository;
    @Autowired
    private StockOverviewRepository stockRepository;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private CallOpportunityRepository callRepository;

    private final Map<String, PutOpportunity> topPutOpportunities = new ConcurrentHashMap<>();
    private final Map<String, CallOpportunity> topCallOpportunities = new ConcurrentHashMap<>();

    @Override
    public void scanMarket() {

        String[] tickers = {"AAPL", "MSFT", "GOOGL", "TSLA", "NVDA"};
        System.out.println("Scanning market at " + new Date());
        logger.info("Starting mock market scan for {} stocks", tickers.length);
        putRepository.deleteAll();
        callRepository.deleteAll();
        topPutOpportunities.clear();
        topCallOpportunities.clear();
        seedPortfolio();

        for (String ticker : tickers) {
            try {

                double stockPrice = getStockPrice(ticker);
                StockOverview overview = getStockOverview(ticker);

                if (stockPrice > 0 && isGoodStock(overview,stockPrice)) {
                    analyzePutOptions(ticker, stockPrice);
                }
            } catch (Exception e) {
                logger.error("Error with {}: {}", ticker, e.getMessage());
            }
        }
        generateCoveredCalls();
        saveAndDisplayTopOpportunities();
    }

    private void seedPortfolio() {

        if (portfolioRepository.count() == 0) {

            Portfolio aapl = new Portfolio();
            aapl.setTicker("AAPL");
            aapl.setShares(100);
            aapl.setCostBasis(150.0); // Mock assignment at $152.50 - $2.50 premium
            aapl.setAcquisitionDate(LocalDateTime.now().minusDays(1));
            portfolioRepository.save(aapl);
            Portfolio msft = new Portfolio();
            msft.setTicker("MSFT");
            msft.setShares(100);
            msft.setCostBasis(380.0);
            msft.setAcquisitionDate(LocalDateTime.now().minusDays(1));
            portfolioRepository.save(msft);
            logger.info("Seeded portfolio with AAPL and MSFT");
        }
    }

    private void generateCoveredCalls() {

        for (Portfolio stock : portfolioRepository.findAll()) {

            double costBasis = stock.getCostBasis();
            double mockPrice = getStockPrice(stock.getTicker());
            double strike = mockPrice * 1.05;

            if (strike > costBasis) {
                CallOpportunity call = new CallOpportunity(stock.getTicker(), strike, 2.0, 0.7);
                topCallOpportunities.put(stock.getTicker(), call);
                logger.info("Generated covered call for {}: Strike ${}, Premium $2.00, PoP 70%", stock.getTicker(), strike);
            }
        }
    }

    private StockOverview getStockOverview(String ticker) {

        Map<String, String> mockData = new HashMap<>();
        mockData.put("Symbol", ticker);
        mockData.put("PERatio", getMockPERatio(ticker));
        mockData.put("EPS", getMockEPS(ticker));
        mockData.put("DividendYield", getMockDividendYield(ticker));
        mockData.put("MarketCapitalization", getMockMarketCap(ticker));
        mockData.put("BookValue", getMockBookValue(ticker));
        StockOverview overview = new StockOverview();
        overview.setTicker(ticker);
        overview.setPeRatio(Double.parseDouble(mockData.get("PERatio")));
        overview.setEps(Double.parseDouble(mockData.get("EPS")));
        overview.setDividendYield(Double.parseDouble(mockData.get("DividendYield")));
        overview.setMarketCap(Long.parseLong(mockData.get("MarketCapitalization")));
        overview.setBookValue(Double.parseDouble(mockData.get("BookValue")));
        overview.setLastUpdated(LocalDateTime.now());
        stockRepository.save(overview);

        return overview;
    }

    private String getMockBookValue(String ticker) {
        return switch (ticker) {
            case "AAPL" -> "4.5";
            case "MSFT" -> "35.0";
            case "GOOGL" -> "25.0";
            case "TSLA" -> "20.0";
            case "NVDA" -> "15.0";
            default -> "0";
        };
    }

    private String getMockMarketCap(String ticker) {
        return switch (ticker) {
            case "AAPL" -> "2700000000000";
            case "MSFT" -> "3100000000000";
            case "GOOGL" -> "2000000000000";
            case "TSLA" -> "800000000000";
            case "NVDA" -> "2300000000000";
            default -> "0";
        };
    }

    private String getMockDividendYield(String ticker) {
        return switch (ticker) {
            case "AAPL" -> "0.005";
            case "MSFT" -> "0.007";
            case "GOOGL" -> "0.0";
            case "TSLA" -> "0.0";
            case "NVDA" -> "0.001";
            default -> "0";
        };
    }

    private String getMockEPS(String ticker) {
        return switch (ticker) {
            case "AAPL" -> "6.5";
            case "MSFT" -> "11.0";
            case "GOOGL" -> "7.8";
            case "TSLA" -> "4.0";
            case "NVDA" -> "8.2";
            default -> "0";
        };
    };

    private void saveAndDisplayTopOpportunities() {
        topPutOpportunities.values().forEach(putRepository::save);
        topCallOpportunities.values().forEach(callRepository::save);
        System.out.println("\nTop Put Opportunities:");
        putRepository.findTop3ByOrderByPremiumDesc().forEach(System.out::println);
        System.out.println("\nTop Covered Call Opportunities:");
        callRepository.findTop3ByOrderByPremiumDesc().forEach(System.out::println);
    }

    private void analyzePutOptions(String ticker, double stockPrice) {

        double strike = stockPrice * 0.95; //mock data
        double premium = 2.5; //mock data
        double pop = 0.75; //mock data

        PutOpportunity opp = new PutOpportunity(ticker, strike, premium, pop);
        topPutOpportunities.put(ticker, opp);
    }

    private boolean isGoodStock(StockOverview overview, double stockPrice) {

        boolean passes = overview.getPeRatio() < 25 && overview.getEps() > 5 && overview.getDividendYield() > 0 && stockPrice > 50;

        if (!passes) {
            logger.info("{} filtered out - P/E: {}, EPS: {}. DividendYield: {}, Price: {}", overview.getTicker(), overview.getPeRatio(),
                    overview.getEps(), overview.getDividendYield(), stockPrice);
        } else {
            logger.info("{} passed filters", overview.getTicker());
        }

        return passes;
    }

    private String getMockPERatio(String ticker) {
        return switch (ticker) {
            case "AAPL" -> "22";
            case "MSFT" -> "24";
            case "GOOGL" -> "20";
            case "TSLA" -> "60";
            case "NVDA" -> "45";
            default -> "9999";
        };
    }

    private double getStockPrice(String ticker) {

        Map<String, String> mockData = new HashMap<>();
        mockData.put("05. price", getMockPrice(ticker));

        return Double.parseDouble(mockData.getOrDefault("05. price", "0"));
    }

    private String getMockPrice(String ticker) {
        return switch (ticker) {
            case "AAPL" -> "160";
            case "MSFT" -> "410.00";
            case "GOOGL" -> "164.30";
            case "TSLA" -> "250.20";
            case "NVDA" -> "950.10";
            default -> "0";
        };
    }

}