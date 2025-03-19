package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.CallOpportunity;
import com.larrydevincarter.optionscanner.entities.Portfolio;
import com.larrydevincarter.optionscanner.entities.PutOpportunity;
import com.larrydevincarter.optionscanner.entities.StockOverview;
import com.larrydevincarter.optionscanner.mappers.StockOverviewMapper;
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
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("api")
public class OptionScannerServiceApiImpl implements OptionScannerService {

    private static final Logger logger = LoggerFactory.getLogger(OptionScannerServiceApiImpl.class);

    @Autowired
    private PutOpportunityRepository putRepository;
    @Autowired
    private StockOverviewRepository stockRepository;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private CallOpportunityRepository callRepository;
    @Autowired
    private StockOverviewMapper mapper;


    private final RestTemplate restTemplate = new RestTemplate();
    private final String API_KEY = "UBE69VEKRS3DTFZE";
    private final Map<String, PutOpportunity> topPutOpportunities = new ConcurrentHashMap<>();
    private final Map<String, CallOpportunity> topCallOpportunities = new ConcurrentHashMap<>();


    @Override
    public void scanMarket() {
        
        String[] tickers = {"AAPL", "MSFT", "GOOGL", "TSLA", "NVDA"};
        System.out.println("Scanning market (api) at " + new Date());
        logger.info("Starting market scan for {} stocks", tickers.length);
        putRepository.deleteAll();
        callRepository.deleteAll();
        topPutOpportunities.clear();
        topCallOpportunities.clear();
        seedPortfolio();
        
        for (String ticker : tickers) {
            try {

                double stockPrice = getStockPrice(ticker);
                StockOverview overview = getStockOverview(ticker);

                if (stockPrice > 0 && isGoodStock(overview, stockPrice)) {
                    analyzePutOptions(ticker, stockPrice);
                }
            } catch (Exception e) {
                logger.error("Error processing {}: {} ", ticker, e.getMessage());
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

        String overviewUrl = String.format(
                "https://www.alphavantage.co/query?function=OVERVIEW&symbol=%s&apikey=%s",
                ticker, API_KEY);
        Map<String, Object> overviewData = restTemplate.getForObject(overviewUrl, Map.class);

        if (overviewData == null || overviewData.containsKey("Note") || overviewData.containsKey("Information")) {
            logger.warn("API limit hit or error for {}", ticker);

            return null;
        }

        StockOverview overview = mapper.toEntity(overviewData);
        stockRepository.save(overview);
        return overview;
    }

    private void saveAndDisplayTopOpportunities() {
        topPutOpportunities.values().forEach(putRepository::save);
        topCallOpportunities.values().forEach(callRepository::save);
        System.out.println("\nTop Put Opportunities:");
        putRepository.findTop3ByOrderByPremiumDesc().forEach(System.out::println);
        System.out.println("\nTop Covered Call Opportunities:");
        callRepository.findTop3ByOrderByPremiumDesc().forEach(System.out::println);
    }

    private void analyzePutOptions(String ticker, double stockPrice) {

        double strike = stockPrice * 0.95;
        double premium = 2.5; //mock data
        double pop = 0.75; //mock data

        PutOpportunity opp = new PutOpportunity(ticker, strike, premium, pop);
        topPutOpportunities.put(ticker, opp);
    }

    private boolean isGoodStock(StockOverview overview, double stockPrice) {

        if (overview == null) return false;

        boolean passes = overview.getPeRatio() < 25 && overview.getEps() > 5 && overview.getDividendYield() > 0 && stockPrice > 50;

        if (!passes) {
            logger.info("{} filtered out - P/E: {}, EPS: {}. DividendYield: {}, Price: {}", overview.getTicker(), overview.getPeRatio(),
                    overview.getEps(), overview.getDividendYield(), stockPrice);
        } else {
            logger.info("{} passed filters", overview.getTicker());
        }

        return passes;

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
