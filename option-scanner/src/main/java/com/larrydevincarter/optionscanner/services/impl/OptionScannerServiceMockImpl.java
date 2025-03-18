package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.PutOpportunity;
import com.larrydevincarter.optionscanner.repositories.PutOpportunityRepository;
import com.larrydevincarter.optionscanner.services.OptionScannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("mock")
public class OptionScannerServiceMockImpl implements OptionScannerService {

    @Autowired
    private PutOpportunityRepository repository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String API_KEY = "UBE69VEKRS3DTFZE";
    private final Map<String, PutOpportunity> topOpportunities = new ConcurrentHashMap<>();

    @Override
    public void scanMarket() {

        String[] tickers = {"AAPL", "MSFT", "GOOGL", "TSLA", "NVDA"};
        System.out.println("Scanning market at " + new Date());

        for (String ticker : tickers) {
            try {

                double stockPrice = getStockPrice(ticker);

                if (isGoodStock(ticker,stockPrice)) {
                    analyzePutOptions(ticker, stockPrice);
                }
            } catch (Exception e) {
                System.err.println("Error with " + ticker + ": " + e.getMessage());
            }
        }
        saveAndDiplayTopOpportunities();
    }

    private void saveAndDiplayTopOpportunities() {

        topOpportunities.values().forEach(repository::save);
        System.out.println("\nTop Put Opportunities:");
        repository.findTop3ByOrderByPremiumDesc().forEach(System.out::println);
    }

    private void analyzePutOptions(String ticker, double stockPrice) {

        double strike = stockPrice * 0.95;
        double premium = 2.5; //mock data
        double pop = 0.75; //mock data

        PutOpportunity opp = new PutOpportunity(ticker, strike, premium, pop);
        topOpportunities.put(ticker, opp);
    }

    private boolean isGoodStock(String ticker, double stockPrice) {

        Map<String, Object> overviewData =new HashMap<>();
        overviewData.put("PERatio", getMockPERatio(ticker));
        String peStr = (String) overviewData.getOrDefault("PERatio", "9999");
        double pe = "N/A".equals(peStr) ? 9999 : Double.parseDouble(peStr);
        System.out.println(ticker + " - P/E: " + pe + ", Price: " + stockPrice);

        return pe < 50 && stockPrice > 50;
    }

    private Object getMockPERatio(String ticker) {
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

        Map<String, Object> globalQuote = new HashMap<>();
        globalQuote.put("05. price", getMockPrice(ticker));
        String priceStr = (String) globalQuote.getOrDefault("05. price", "0");

        return Double.parseDouble(priceStr);
    }

    private Object getMockPrice(String ticker) {
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