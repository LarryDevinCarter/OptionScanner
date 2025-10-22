package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.entities.CashFlow;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.CashFlowRepository;
import com.larrydevincarter.optionscanner.services.CashFlowService;
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
        Map<String, CashFlow> uniqueCashFlows = new LinkedHashMap<>();

        for (Map<String, String> report : reports) {
            CashFlow cashFlow = new CashFlow();
            cashFlow.setSymbol(symbol);
            LocalDate fiscalDate;
            try {
                fiscalDate = LocalDate.parse(report.get("fiscalDateEnding"));
            } catch (Exception e) {
                String errorMsg = "Invalid fiscalDateEnding for symbol " + symbol + ": " + report.get("fiscalDateEnding");
                log.warn(errorMsg);
                errorLog.add(errorMsg);
                continue;
            }
            cashFlow.setFiscalDateEnding(fiscalDate);
            cashFlow.setReportType(reportType);
            cashFlow.setReportedCurrency(report.get("reportedCurrency"));
            cashFlow.setOperatingCashflow(parseDouble(report.get("operatingCashflow"), errorLog));
            cashFlow.setCapitalExpenditures(parseDouble(report.get("capitalExpenditures"), errorLog));
            cashFlow.setLastUpdated(LocalDateTime.now());

            String key = fiscalDate + "|" + reportType;
            if (uniqueCashFlows.containsKey(key)) {
                log.warn("Duplicate cash flow report in API response for {} - {} ({})", symbol, fiscalDate, reportType);
                errorLog.add("Duplicate cash flow report in API response: " + symbol + " - " + fiscalDate + " (" + reportType + ")");
            }
            uniqueCashFlows.put(key, cashFlow);
        }
        return new ArrayList<>(uniqueCashFlows.values());
    }

    private Double parseDouble(String value, List<String> errorLog) {
        if (value == null || "None".equals(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            String errorMsg = "Failed to parse value as Double: " + value;
            log.warn(errorMsg);
            errorLog.add(errorMsg);
            return null;
        }
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