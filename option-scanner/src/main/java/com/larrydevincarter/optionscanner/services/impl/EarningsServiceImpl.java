package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.EarningsRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.EarningsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EarningsServiceImpl implements EarningsService {

    private final EarningsRepository earningsRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final RestTemplate restTemplate;

    @Value("${alphavantage.api.key}")
    private String apiKey;
    @Value("${alphavantage.api.base-url}")
    private String baseUrl;

    private static final int CALLS_PER_MINUTE = 75;
    private static final long DELAY_MS = 60_000;
    private static final int MAX_RETRIES = 3;

    @Override
    public void fetchAndStoreEarnings(List<String> errorLog) {

        List<String> symbols = getSymbolsWithUpdatedIncomeStatements();
        log.info("Fetching earnings for {} symbols with updated income statements", symbols.size());
        int callCount = 0;

        for (String symbol : symbols) {
            if (callCount >= CALLS_PER_MINUTE) {

                log.info("Hit rate limit (75 calls/minute). Pausing for 1 minute...");

                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    log.error("Interrupted during rate limit delay: {}", e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for symbol " + symbol + ": " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
                callCount = 0;
            }
            String url = String.format("%s/query?function=EARNINGS&symbol=%s&apikey=%s", baseUrl, symbol, apiKey);
            Map<String, Object> responseBody = null;
            int attempt = 0;

            while (attempt < MAX_RETRIES) {
                try {
                    responseBody = restTemplate.getForObject(url, Map.class);
                    callCount++;
                    break;
                } catch (ResourceAccessException e) {

                    attempt++;
                    String errorMsg = "Attempt " + attempt + " failed for " + symbol + ": " + e.getMessage();
                    log.warn(errorMsg);
                    errorLog.add(errorMsg);

                    if (attempt == MAX_RETRIES) {
                        errorLog.add("Max retries reached for " + symbol + ". Skipping.");
                        break;
                    }
                }
            }
            if (responseBody != null) {
                processEarnings(symbol, responseBody, errorLog);
            } else {
                errorLog.add("Failed to fetch earnings for symbol: " + symbol + " after " + MAX_RETRIES + " attempts.");
            }
        }
    }

    @Transactional
    private void processEarnings(String symbol, Map<String, Object> response, List<String> errorLog) {

        earningsRepository.deleteBySymbol(symbol);
        log.info("Deleted existing earnings for symbol: {}", symbol);
        List<Earnings> earningsList = new ArrayList<>();
        List<Map<String, String>> annualEarnings = (List<Map<String, String>>) response.get("annualEarnings");

        if (annualEarnings != null) {
            earningsList.addAll(parseEarnings(annualEarnings, symbol, "annual", errorLog));
        }
        List<Map<String, String>> quarterlyEarnings = (List<Map<String, String>>) response.get("quarterlyEarnings");

        if (quarterlyEarnings != null) {
            earningsList.addAll(parseEarnings(quarterlyEarnings,symbol, "quarterly", errorLog));
        }
        earningsRepository.saveAll(earningsList);
        log.info("Stored {} earnings records for symbol: {}", earningsList.size(), symbol);
    }

    private List<Earnings> parseEarnings(List<Map<String, String>> reports, String symbol, String reportType, List<String> errorLog) {

        List<Earnings> earningsList = new ArrayList<>();

        for (Map<String, String> report : reports) {

            Earnings earning = new Earnings();
            earning.setSymbol(symbol);
            earning.setFiscalDateEnding(LocalDate.parse(report.get("fiscalDateEnding")));
            earning.setReportType(reportType);
            earning.setReportedEPS(parseDouble(report.get("reportedEPS"), errorLog));

            if ("quarterly".equals(reportType)) {

                earning.setReportedDate(report.get("reportedDate") !=null ? LocalDate.parse(report.get("reportedDate")) : null);
                earning.setEstimatedEPS(parseDouble(report.get("estimatedEPS"), errorLog));
                earning.setSurprise(parseDouble(report.get("surprise"), errorLog));
                earning.setSurprisePercentage(parseDouble(report.get("surprisePercentage"), errorLog));
            }
            earning.setLastUpdated(LocalDateTime.now());
            earningsList.add(earning);
        }
        return earningsList;
    }

    private Double parseDouble(String value, List<String> errorLog) {
        if (value ==  null || "None".equals(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            String errorMsg = "Failed to parse to parse value as Double: " + value;
            log.warn(errorMsg);
            errorLog.add(errorMsg);
            return null;
        }
    }

    @Override
    public List<String> getSymbolsWithUpdatedIncomeStatements() {

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return incomeStatementRepository.findAll().stream()
                .filter(i -> i.getLastUpdated().isAfter(startOfDay))
                .map(IncomeStatement::getSymbol)
                .distinct().collect(Collectors.toList());
    }
}
