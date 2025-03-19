package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.PutOpportunity;
import com.larrydevincarter.optionscanner.entities.StockOverview;
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

    private final Map<String, PutOpportunity> topOpportunities = new ConcurrentHashMap<>();

    @Override
    public void scanMarket() {

        String[] tickers = {"AAPL", "MSFT", "GOOGL", "TSLA", "NVDA"};
        System.out.println("Scanning market at " + new Date());
        logger.info("Starting mock market scan for {} stocks", tickers.length);

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
        saveAndDisplayTopOpportunities();
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

        topOpportunities.values().forEach(putRepository::save);
        System.out.println("\nTop Put Opportunities:");
        putRepository.findTop3ByOrderByPremiumDesc().forEach(System.out::println);
    }

    private void analyzePutOptions(String ticker, double stockPrice) {

        double strike = stockPrice * 0.95; //mock data
        double premium = 2.5; //mock data
        double pop = 0.75; //mock data

        PutOpportunity opp = new PutOpportunity(ticker, strike, premium, pop);
        topOpportunities.put(ticker, opp);
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
            case "AAPL" -> "30";
            case "MSFT" -> "35";
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
            case "AAPL" -> "175.50";
            case "MSFT" -> "420.75";
            case "GOOGL" -> "164.30";
            case "TSLA" -> "250.20";
            case "NVDA" -> "950.10";
            default -> "0";
        };
    }
}