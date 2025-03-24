package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.IncomeStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncomeStatementServiceImpl implements IncomeStatementService {

    private final IncomeStatementRepository repository;
    private final RestTemplate restTemplate;

    @Value("${alphavantage.api.key}")
    private String apiKey;
    @Value("${alphavantage.api.base-url}")
    private String baseUrl;

    private static final int CALLS_PER_MINUTE = 75;
    private static final long DELAY_MS = 60_000;
    private static final int MAX_RETRIES = 3;
    private final List<String> errorLog = new ArrayList<>();

    @Override
    @Transactional
    public void fetchAndStoreIncomeStatements() {

        errorLog.clear();
        List<String> symbols = getSymbolsNeedingUpdate();
        log.info("Number of Symbols to update INCOME_STATEMENTS for: " + symbols.size());
        int callCount = 0;
        log.info("Starting fetching income statements");

        for (String symbol : symbols) {

            if (callCount >= CALLS_PER_MINUTE) {

                log.info("Hit rate limit (75 calls/minute). Pausing for 1 minute...");

                try {
                    Thread.sleep(DELAY_MS);
                } catch (Exception e) {
                    log.error("Interrupted during rate limit delay: {}", e.getMessage());
                    errorLog.add("Interrupted during rate limit delay for symbol " + symbol + ": " +  e.getMessage());
                    Thread.currentThread().interrupt();
                }
                callCount = 0;
            }
            String url = String.format("%s/query?function=INCOME_STATEMENT&symbol=%s&apikey=%s", baseUrl, symbol, apiKey);
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
                    try {
                        Thread.sleep(DELAY_MS/ 2);
                    } catch (InterruptedException ie) {
                        log.error("Interrupted during retry delay: {}", ie.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (responseBody != null) {
                processIncomeStatements(symbol, responseBody);
            } else {
                errorLog.add("Failed to fetch income statement for symbol: " + symbol + " after " + MAX_RETRIES + " attempts.");
            }
        }
        writeErrorReport();
    }

    private void writeErrorReport() {

        if (errorLog.isEmpty()) {
            log.info("No errors to report.");
            return;
        }
        String directory = "logs/errors/";
        new File(directory).mkdirs();
        String filename = directory + "error_report_" + LocalDateTime.now().toString().replace(":", "-") + ".txt";

        try (FileWriter writer = new FileWriter(filename)){

            writer.write("Error Report - Option Scanner Revenue Data Fetch\n");
            writer.write("Timestamp: " +LocalDateTime.now() +"\n");
            writer.write("Total Errors: " + errorLog.size() + "\n\n");

            for (String error: errorLog) {
                writer.write(error + "\n");
            }

            log.info("Error report written to {}", filename);
        } catch (IOException e) {
            log.error("Failed to write error report: {}", e.getMessage());
        }
    }

    private void processIncomeStatements(String symbol, Map<String, Object> response) {

        repository.deleteBySymbol(symbol);
        log.info("Deleted existing income statements for symbol: {}", symbol);
        List<IncomeStatement> statements = new ArrayList<>();
        List<Map<String, String>> annualReports = (List<Map<String, String>>) response.get("annualReports");

        if (annualReports != null) {
            statements.addAll(parseReports(annualReports, symbol, "annual"));
        }
        List<Map<String, String>> quarterlyReports = (List<Map<String, String>>) response.get("quarterlyReports");

        if (quarterlyReports != null) {
            statements.addAll(parseReports(quarterlyReports, symbol, "quarterly"));
        }
        repository.saveAll(statements);
        log.info("Stored {} income statements for symbol: {}", statements.size(), symbol);
    }

    private List<IncomeStatement> parseReports(List<Map<String, String>> reports, String symbol, String reportType) {

        Map<String, IncomeStatement> uniqueStatements = new LinkedHashMap<>();

        for (Map<String, String> report : reports) {

            IncomeStatement statement = new IncomeStatement();
            statement.setSymbol(symbol);
            LocalDate fiscalDate = LocalDate.parse(report.get("fiscalDateEnding"));
            statement.setFiscalDateEnding(fiscalDate);
            statement.setReportType(reportType);
            statement.setReportedCurrency(report.get("reportedCurrency"));
            statement.setGrossProfit(parseDouble(report.get("grossProfit")));
            statement.setTotalRevenue(parseDouble(report.get("totalRevenue")));
            statement.setCostOfRevenue(parseDouble(report.get("costOfRevenue")));
            statement.setCostOfGoodsAndServicesSold(parseDouble(report.get("costofGoodsAndServicesSold")));
            statement.setOperatingIncome(parseDouble(report.get("operatingIncome")));
            statement.setSellingGeneralAndAdministrative(parseDouble(report.get("sellingGeneralAndAdministrative")));
            statement.setResearchAndDevelopment(parseDouble(report.get("researchAndDevelopment")));
            statement.setOperatingExpenses(parseDouble(report.get("operatingExpenses")));
            statement.setInvestmentIncomeNet(parseDouble(report.get("investmentIncomeNet")));
            statement.setNetInterestIncome(parseDouble(report.get("netInterestIncome")));
            statement.setInterestIncome(parseDouble(report.get("interestIncome")));
            statement.setInterestExpense(parseDouble(report.get("interestExpense")));
            statement.setNonInterestIncome(parseDouble(report.get("nonInterestIncome")));
            statement.setOtherNonOperatingIncome(parseDouble(report.get("otherNonOperatingIncome")));
            statement.setDepreciation(parseDouble(report.get("depreciation")));
            statement.setDepreciationAndAmortization(parseDouble(report.get("depreciationAndAmortization")));
            statement.setIncomeBeforeTax(parseDouble(report.get("incomeBeforeTax")));
            statement.setIncomeTaxExpense(parseDouble(report.get("incomeTaxExpense")));
            statement.setInterestAndDebtExpense(parseDouble(report.get("interestAndDebtExpense")));
            statement.setNetIncomeFromContinuingOperations(parseDouble(report.get("netIncomeFromContinuingOperations")));
            statement.setComprehensiveIncomeNetOfTax(parseDouble(report.get("comprehensiveIncomeNetOfTax")));
            statement.setEbit(parseDouble(report.get("ebit")));
            statement.setEbitda(parseDouble(report.get("ebitda")));
            statement.setNetIncome(parseDouble(report.get("netIncome")));
            statement.setLastUpdated(LocalDateTime.now());
            String key = fiscalDate + "|" + reportType;

            if (uniqueStatements.containsKey(key)) {
                log.warn("Duplicate report in API response for {} - {} ({})", symbol, fiscalDate, reportType);
                errorLog.add("Duplicate in API response: " + symbol + " - " + fiscalDate + " (" + reportType + ")");
            }
            uniqueStatements.put(key, statement);
        }
        return new ArrayList<>(uniqueStatements.values());
    }

    private Double parseDouble(String value) {
        if (value == null || "None".equals(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            String errorMsg = "Failed to parse value as Double: " + value;
            log.warn("{}", errorMsg);
            errorLog.add(errorMsg);
            return null;
        }
    }

    @Override
    public List<String> getSymbolsNeedingUpdate() {
        LocalDate ninetyDaysAgo = LocalDate.now().minusDays(90);
        return repository.findActiveTradableSymbolsNeedingUpdate(ninetyDaysAgo);
    }

}
