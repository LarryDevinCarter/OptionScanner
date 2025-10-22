package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.entities.CashFlow;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.CashFlowRepository;
import com.larrydevincarter.optionscanner.services.CashFlowService;
import com.larrydevincarter.optionscanner.utils.FinancialReportParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashFlowServiceImpl implements CashFlowService {

    private final CashFlowRepository cashFlowRepository;
    private final AssetRepository assetRepository;

    @Override
    @Transactional
    public void processCashFlows(String symbol, Map<String, Object> response, List<String> errorLog) {
        cashFlowRepository.deleteBySymbol(symbol);
        cashFlowRepository.flush();
        log.info("Deleted existing cash flows for symbol: {}", symbol);

        List<CashFlow> cashFlows = new ArrayList<>();
        List<Map<String, String>> annualReports = (List<Map<String, String>>) response.get("annualReports");

        if (annualReports != null) {
            cashFlows.addAll(parseReports(annualReports, symbol, "annual", errorLog));
        }

        List<Map<String, String>> quarterlyReports = (List<Map<String, String>>) response.get("quarterlyReports");

        if (quarterlyReports != null) {
            cashFlows.addAll(parseReports(quarterlyReports, symbol, "quarterly", errorLog));
        }

        cashFlowRepository.saveAll(cashFlows);
        cashFlowRepository.flush();
        log.info("Stored {} cash flow records for symbol: {}", cashFlows.size(), symbol);
    }

    private List<CashFlow> parseReports(List<Map<String, String>> reports, String symbol, String reportType, List<String> errorLog) {
        return FinancialReportParser.parseReportsWithFiscalDate(reports, symbol, reportType, errorLog, report -> {
            CashFlow cf = new CashFlow();
            cf.setSymbol(symbol);
            cf.setFiscalDateEnding(LocalDate.parse(report.get("fiscalDateEnding")));
            cf.setReportType(reportType);
            cf.setReportedCurrency(report.get("reportedCurrency"));
            cf.setOperatingCashflow(FinancialReportParser.parseDouble(report.get("operatingCashflow"), errorLog));
            cf.setCapitalExpenditures(FinancialReportParser.parseDouble(report.get("capitalExpenditures"), errorLog));
            cf.setLastUpdated(LocalDateTime.now());
            return cf;
        });
    }

    @Override
    public List<String> getSymbolsNeedingUpdate(List<String> symbols) {
        LocalDate date = LocalDate.now().minusDays(120);
        return cashFlowRepository.findSymbolsNeedingUpdate(date, symbols);
    }

    @Override
    public List<String> getSymbolsThatHaveStatements(List<String> symbols) {
        return cashFlowRepository.findSymbolsWithData(symbols);
    }
}