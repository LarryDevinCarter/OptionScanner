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
@Profile("api")
public class OptionScannerServiceApiImpl implements OptionScannerService {

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

        String overviewUrl = String.format(
                "https://www.alphavantage.co/query?function=OVERVIEW&symbol=%s&apikey=%s",
                ticker, API_KEY);
        Map<String, Object> overviewData = restTemplate.getForObject(overviewUrl, Map.class);

        if (overviewData == null || overviewData.containsKey("Note") || overviewData.containsKey("Information")) {
            System.err.println("API limit hit or error for " + ticker);

            return false;
        }

        String peStr = (String) overviewData.getOrDefault("PERatio", "9999");
        double pe = "N/A".equals(peStr) ? 9999 : Double.parseDouble(peStr);
        System.out.println(ticker + " - P/E: " + pe + ", Price: " + stockPrice);

        return pe < 50 && stockPrice > 50;
    }

    private double getStockPrice(String ticker) {

        String quoteUrl = String.format(
                "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                ticker, API_KEY);
        Map<String, Object> quoteData = restTemplate.getForObject(quoteUrl, Map.class);

        if (quoteUrl == null || !quoteData.containsKey("Global Quote")) {
            return 0;
        }

        Map <String, Object> globalQuote = (Map<String, Object>) quoteData.get("Global Quote");
        String priceStr = (String) globalQuote.getOrDefault("05. price", "0");

        return Double.parseDouble(priceStr);
    }
}
