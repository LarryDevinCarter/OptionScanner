package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.IncomeStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public void processIncomeStatements(String symbol, Map<String, Object> response, List<String> errorLog) {

        repository.deleteBySymbol(symbol);
        log.info("Deleted existing income statements for symbol: {}", symbol);
        List<IncomeStatement> statements = new ArrayList<>();
        List<Map<String, String>> annualReports = (List<Map<String, String>>) response.get("annualReports");

        if (annualReports != null) {
            statements.addAll(parseReports(annualReports, symbol, "annual", errorLog));
        }
        List<Map<String, String>> quarterlyReports = (List<Map<String, String>>) response.get("quarterlyReports");

        if (quarterlyReports != null) {
            statements.addAll(parseReports(quarterlyReports, symbol, "quarterly", errorLog));
        }
        repository.saveAll(statements);
        log.info("Stored {} income statements for symbol: {}", statements.size(), symbol);
    }

    private List<IncomeStatement> parseReports(List<Map<String, String>> reports, String symbol, String reportType, List<String> errorLog) {

        Map<String, IncomeStatement> uniqueStatements = new LinkedHashMap<>();

        for (Map<String, String> report : reports) {

            IncomeStatement statement = new IncomeStatement();
            statement.setSymbol(symbol);
            LocalDate fiscalDate = LocalDate.parse(report.get("fiscalDateEnding"));
            statement.setFiscalDateEnding(fiscalDate);
            statement.setReportType(reportType);
            statement.setReportedCurrency(report.get("reportedCurrency"));
            statement.setGrossProfit(parseDouble(report.get("grossProfit"), errorLog));
            statement.setTotalRevenue(parseDouble(report.get("totalRevenue"), errorLog));
            statement.setCostOfRevenue(parseDouble(report.get("costOfRevenue"), errorLog));
            statement.setCostOfGoodsAndServicesSold(parseDouble(report.get("costofGoodsAndServicesSold"), errorLog));
            statement.setOperatingIncome(parseDouble(report.get("operatingIncome"), errorLog));
            statement.setSellingGeneralAndAdministrative(parseDouble(report.get("sellingGeneralAndAdministrative"), errorLog));
            statement.setResearchAndDevelopment(parseDouble(report.get("researchAndDevelopment"), errorLog));
            statement.setOperatingExpenses(parseDouble(report.get("operatingExpenses"), errorLog));
            statement.setInvestmentIncomeNet(parseDouble(report.get("investmentIncomeNet"), errorLog));
            statement.setNetInterestIncome(parseDouble(report.get("netInterestIncome"), errorLog));
            statement.setInterestIncome(parseDouble(report.get("interestIncome"), errorLog));
            statement.setInterestExpense(parseDouble(report.get("interestExpense"), errorLog));
            statement.setNonInterestIncome(parseDouble(report.get("nonInterestIncome"), errorLog));
            statement.setOtherNonOperatingIncome(parseDouble(report.get("otherNonOperatingIncome"), errorLog));
            statement.setDepreciation(parseDouble(report.get("depreciation"), errorLog));
            statement.setDepreciationAndAmortization(parseDouble(report.get("depreciationAndAmortization"), errorLog));
            statement.setIncomeBeforeTax(parseDouble(report.get("incomeBeforeTax"), errorLog));
            statement.setIncomeTaxExpense(parseDouble(report.get("incomeTaxExpense"), errorLog));
            statement.setInterestAndDebtExpense(parseDouble(report.get("interestAndDebtExpense"), errorLog));
            statement.setNetIncomeFromContinuingOperations(parseDouble(report.get("netIncomeFromContinuingOperations"), errorLog));
            statement.setComprehensiveIncomeNetOfTax(parseDouble(report.get("comprehensiveIncomeNetOfTax"), errorLog));
            statement.setEbit(parseDouble(report.get("ebit"), errorLog));
            statement.setEbitda(parseDouble(report.get("ebitda"), errorLog));
            statement.setNetIncome(parseDouble(report.get("netIncome"), errorLog));
            statement.setLastUpdated(LocalDateTime.now());
            String key = fiscalDate + "|" + reportType;

            if (uniqueStatements.containsKey(key)) {
                log.warn("Duplicate income statement report in API response for {} - {} ({})", symbol, fiscalDate, reportType);
                errorLog.add("Duplicate income statement report in API response: " + symbol + " - " + fiscalDate + " (" + reportType + ")");
            }
            uniqueStatements.put(key, statement);
        }
        return new ArrayList<>(uniqueStatements.values());
    }

    private Double parseDouble(String value, List<String> errorLog) {
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
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        return repository.findActiveTradableSymbolsNeedingUpdate(thirtyDaysAgo);
    }

}
