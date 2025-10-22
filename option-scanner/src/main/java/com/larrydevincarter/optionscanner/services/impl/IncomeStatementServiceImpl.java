package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.IncomeStatementService;
import com.larrydevincarter.optionscanner.utils.FinancialReportParser;
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

    private final IncomeStatementRepository incomeStatementRepository;
    private final AssetRepository assetRepository;

    @Override
    @Transactional
    public void processIncomeStatements(String symbol, Map<String, Object> response, List<String> errorLog) {

        incomeStatementRepository.deleteBySymbol(symbol);
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
        incomeStatementRepository.saveAll(statements);
        log.info("Stored {} income statements for symbol: {}", statements.size(), symbol);
    }

    private List<IncomeStatement> parseReports(List<Map<String, String>> reports, String symbol, String reportType, List<String> errorLog) {

        return FinancialReportParser.parseReportsWithFiscalDate(reports, symbol, reportType, errorLog, report -> {
            IncomeStatement stmt = new IncomeStatement();
            stmt.setSymbol(symbol);
            stmt.setFiscalDateEnding(LocalDate.parse(report.get("fiscalDateEnding")));
            stmt.setReportType(reportType);
            stmt.setReportedCurrency(report.get("reportedCurrency"));
            stmt.setGrossProfit(FinancialReportParser.parseDouble(report.get("grossProfit"), errorLog));
            stmt.setTotalRevenue(FinancialReportParser.parseDouble(report.get("totalRevenue"), errorLog));
            stmt.setCostOfRevenue(FinancialReportParser.parseDouble(report.get("costOfRevenue"), errorLog));
            stmt.setCostOfGoodsAndServicesSold(FinancialReportParser.parseDouble(report.get("costofGoodsAndServicesSold"), errorLog));
            stmt.setOperatingIncome(FinancialReportParser.parseDouble(report.get("operatingIncome"), errorLog));
            stmt.setSellingGeneralAndAdministrative(FinancialReportParser.parseDouble(report.get("sellingGeneralAndAdministrative"), errorLog));
            stmt.setResearchAndDevelopment(FinancialReportParser.parseDouble(report.get("researchAndDevelopment"), errorLog));
            stmt.setOperatingExpenses(FinancialReportParser.parseDouble(report.get("operatingExpenses"), errorLog));
            stmt.setInvestmentIncomeNet(FinancialReportParser.parseDouble(report.get("investmentIncomeNet"), errorLog));
            stmt.setNetInterestIncome(FinancialReportParser.parseDouble(report.get("netInterestIncome"), errorLog));
            stmt.setInterestIncome(FinancialReportParser.parseDouble(report.get("interestIncome"), errorLog));
            stmt.setInterestExpense(FinancialReportParser.parseDouble(report.get("interestExpense"), errorLog));
            stmt.setNonInterestIncome(FinancialReportParser.parseDouble(report.get("nonInterestIncome"), errorLog));
            stmt.setOtherNonOperatingIncome(FinancialReportParser.parseDouble(report.get("otherNonOperatingIncome"), errorLog));
            stmt.setDepreciation(FinancialReportParser.parseDouble(report.get("depreciation"), errorLog));
            stmt.setDepreciationAndAmortization(FinancialReportParser.parseDouble(report.get("depreciationAndAmortization"), errorLog));
            stmt.setIncomeBeforeTax(FinancialReportParser.parseDouble(report.get("incomeBeforeTax"), errorLog));
            stmt.setIncomeTaxExpense(FinancialReportParser.parseDouble(report.get("incomeTaxExpense"), errorLog));
            stmt.setInterestAndDebtExpense(FinancialReportParser.parseDouble(report.get("interestAndDebtExpense"), errorLog));
            stmt.setNetIncomeFromContinuingOperations(FinancialReportParser.parseDouble(report.get("netIncomeFromContinuingOperations"), errorLog));
            stmt.setComprehensiveIncomeNetOfTax(FinancialReportParser.parseDouble(report.get("comprehensiveIncomeNetOfTax"), errorLog));
            stmt.setEbit(FinancialReportParser.parseDouble(report.get("ebit"), errorLog));
            stmt.setEbitda(FinancialReportParser.parseDouble(report.get("ebitda"), errorLog));
            stmt.setNetIncome(FinancialReportParser.parseDouble(report.get("netIncome"), errorLog));
            stmt.setLastUpdated(LocalDateTime.now());
            return stmt;
        });
    }

    @Override
    public List<String> getSymbolsNeedingUpdate(List<String> symbols) {
        LocalDate date = LocalDate.now().minusDays(120);
        return incomeStatementRepository.findSymbolsNeedingUpdate(date, symbols);
    }

    @Override
    public List<String> getSymbolsThatHaveStatements(List<String> symbols) {
        return incomeStatementRepository.findSymbolsWithData(symbols);
    }

}
