package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.repositories.BalanceSheetRepository;
import com.larrydevincarter.optionscanner.services.BalanceSheetService;
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
public class BalanceSheetServiceImpl implements BalanceSheetService {

    private final BalanceSheetRepository balanceSheetRepository;

    @Override
    @Transactional
    public void processBalanceSheets(String symbol, Map<String, Object> response, List<String> errorLog) {

        balanceSheetRepository.deleteBySymbol(symbol);
        balanceSheetRepository.flush();
        log.info("Deleted existing balance sheets for symbol: {}", symbol);
        List<BalanceSheet> balanceSheets = new ArrayList<>();
        List<Map<String, String>> annualReports = (List<Map<String, String>>) response.get("annualReports");

        if (annualReports != null) {
            balanceSheets.addAll(parseReports(annualReports, symbol, "annual", errorLog));
        }
        List<Map<String, String>> quarterlyReports = (List<Map<String, String>>) response.get("quarterlyReports");

        if (quarterlyReports != null) {
            balanceSheets.addAll(parseReports(quarterlyReports, symbol, "quarterly", errorLog));
        }
        balanceSheetRepository.saveAll(balanceSheets);
        balanceSheetRepository.flush();
        log.info("Stored {} balance sheet records for symbol: {}", balanceSheets.size(), symbol);
    }

    private List<BalanceSheet> parseReports(List<Map<String, String>> reports, String symbol, String reportType, List<String> errorLog) {

        Map<String, BalanceSheet> uniqueBalanceSheets = new LinkedHashMap<>();

        for (Map<String, String> report : reports) {
            BalanceSheet balanceSheet = new BalanceSheet();
            balanceSheet.setSymbol(symbol);
            LocalDate fiscalDate = LocalDate.parse(report.get("fiscalDateEnding"));
            balanceSheet.setFiscalDateEnding(fiscalDate);
            balanceSheet.setReportType(reportType);
            balanceSheet.setReportedCurrency(report.get("reportedCurrency"));
            balanceSheet.setTotalAssets(parseLong(report.get("totalAssets"), errorLog));
            balanceSheet.setTotalCurrentAssets(parseLong(report.get("totalCurrentAssets"), errorLog));
            balanceSheet.setCashAndCashEquivalentsAtCarryingValue(parseLong(report.get("cashAndCashEquivalentsAtCarryingValue"), errorLog));
            balanceSheet.setCashAndShortTermInvestments(parseLong(report.get("cashAndShortTermInvestments"), errorLog));
            balanceSheet.setInventory(parseLong(report.get("inventory"), errorLog));
            balanceSheet.setCurrentNetReceivables(parseLong(report.get("currentNetReceivables"), errorLog));
            balanceSheet.setTotalNonCurrentAssets(parseLong(report.get("totalNonCurrentAssets"), errorLog));
            balanceSheet.setIntangibleAssets(parseLong(report.get("intangibleAssets"), errorLog));
            balanceSheet.setIntangibleAssetsExcludingGoodwill(parseLong(report.get("intangibleAssetsExcludingGoodwill"), errorLog));
            balanceSheet.setGoodwill(parseLong(report.get("goodwill"), errorLog));
            balanceSheet.setTotalLiabilities(parseLong(report.get("totalLiabilities"), errorLog));
            balanceSheet.setTotalCurrentLiabilities(parseLong(report.get("totalCurrentLiabilities"), errorLog));
            balanceSheet.setCurrentAccountsPayable(parseLong(report.get("currentAccountsPayable"), errorLog));
            balanceSheet.setShortTermDebt(parseLong(report.get("shortTermDebt"), errorLog));
            balanceSheet.setTotalNonCurrentLiabilities(parseLong(report.get("totalNonCurrentLiabilities"), errorLog));
            balanceSheet.setCapitalLeaseObligations(parseLong(report.get("capitalLeaseObligations"), errorLog));
            balanceSheet.setLongTermDebt(parseLong(report.get("longTermDebt"), errorLog));
            balanceSheet.setCurrentLongTermDebt(parseLong(report.get("currentLongTermDebt"), errorLog));
            balanceSheet.setShortLongTermDebtTotal(parseLong(report.get("shortLongTermDebtTotal"), errorLog));
            balanceSheet.setOtherCurrentLiabilities(parseLong(report.get("otherCurrentLiabilities"), errorLog));
            balanceSheet.setOtherNonCurrentLiabilities(parseLong(report.get("otherNonCurrentLiabilities"), errorLog));
            balanceSheet.setTotalShareholderEquity(parseLong(report.get("totalShareholderEquity"), errorLog));
            balanceSheet.setRetainedEarnings(parseLong(report.get("retainedEarnings"), errorLog));
            balanceSheet.setCommonStock(parseLong(report.get("commonStock"), errorLog));
            balanceSheet.setCommonStockSharesOutstanding(parseLong(report.get("commonStockSharesOutstanding"), errorLog));
            balanceSheet.setLastUpdated(LocalDateTime.now());
            String key = fiscalDate + "|" + reportType;

            if (uniqueBalanceSheets.containsKey(key)) {
                log.warn("Duplicate balance sheet report in API response for {} - {} ({})", symbol, fiscalDate, reportType);
                errorLog.add("Duplicate balance sheet report in API response: " + symbol + " - " + fiscalDate + " (" + reportType + ")");
            }
            uniqueBalanceSheets.put(key, balanceSheet);
        }
        return new ArrayList<>(uniqueBalanceSheets.values());
    }

    private Long parseLong(String value, List<String> errorLog) {
        if (value == null || "None".equals(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            String errorMsg = "Failed to parse value as Long: " + value;
            log.warn(errorMsg);
            errorLog.add(errorMsg);
            return null;
        }
    }

    @Override
    public List<String> getSymbolsNeedingUpdate() {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        return balanceSheetRepository.findActiveTradableSymbolsNeedingUpdate(thirtyDaysAgo);
    }
}