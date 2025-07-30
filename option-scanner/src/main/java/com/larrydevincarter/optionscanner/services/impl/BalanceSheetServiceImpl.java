package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.BalanceSheetRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
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
    private final AssetRepository assetRepository;
    private final IncomeStatementRepository incomeStatementRepository;

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
            balanceSheet.setTotalAssets(parseDouble(report.get("totalAssets"), errorLog));
            balanceSheet.setTotalCurrentAssets(parseDouble(report.get("totalCurrentAssets"), errorLog));
            balanceSheet.setCashAndCashEquivalentsAtCarryingValue(parseDouble(report.get("cashAndCashEquivalentsAtCarryingValue"), errorLog));
            balanceSheet.setCashAndShortTermInvestments(parseDouble(report.get("cashAndShortTermInvestments"), errorLog));
            balanceSheet.setInventory(parseDouble(report.get("inventory"), errorLog));
            balanceSheet.setCurrentNetReceivables(parseDouble(report.get("currentNetReceivables"), errorLog));
            balanceSheet.setTotalNonCurrentAssets(parseDouble(report.get("totalNonCurrentAssets"), errorLog));
            balanceSheet.setIntangibleAssets(parseDouble(report.get("intangibleAssets"), errorLog));
            balanceSheet.setIntangibleAssetsExcludingGoodwill(parseDouble(report.get("intangibleAssetsExcludingGoodwill"), errorLog));
            balanceSheet.setGoodwill(parseDouble(report.get("goodwill"), errorLog));
            balanceSheet.setTotalLiabilities(parseDouble(report.get("totalLiabilities"), errorLog));
            balanceSheet.setTotalCurrentLiabilities(parseDouble(report.get("totalCurrentLiabilities"), errorLog));
            balanceSheet.setCurrentAccountsPayable(parseDouble(report.get("currentAccountsPayable"), errorLog));
            balanceSheet.setShortTermDebt(parseDouble(report.get("shortTermDebt"), errorLog));
            balanceSheet.setTotalNonCurrentLiabilities(parseDouble(report.get("totalNonCurrentLiabilities"), errorLog));
            balanceSheet.setCapitalLeaseObligations(parseDouble(report.get("capitalLeaseObligations"), errorLog));
            balanceSheet.setLongTermDebt(parseDouble(report.get("longTermDebt"), errorLog));
            balanceSheet.setCurrentLongTermDebt(parseDouble(report.get("currentLongTermDebt"), errorLog));
            balanceSheet.setShortLongTermDebtTotal(parseDouble(report.get("shortLongTermDebtTotal"), errorLog));
            balanceSheet.setOtherCurrentLiabilities(parseDouble(report.get("otherCurrentLiabilities"), errorLog));
            balanceSheet.setOtherNonCurrentLiabilities(parseDouble(report.get("otherNonCurrentLiabilities"), errorLog));
            balanceSheet.setTotalShareholderEquity(parseDouble(report.get("totalShareholderEquity"), errorLog));
            balanceSheet.setRetainedEarnings(parseDouble(report.get("retainedEarnings"), errorLog));
            balanceSheet.setCommonStock(parseDouble(report.get("commonStock"), errorLog));
            balanceSheet.setCommonStockSharesOutstanding(parseDouble(report.get("commonStockSharesOutstanding"), errorLog));
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
        return balanceSheetRepository.findSymbolsNeedingUpdate(date, symbols);
    }

    @Override
    public List<String> getSymbolsThatHaveStatements(List<String> symbols) {
        return balanceSheetRepository.findSymbolsWithData(symbols);
    }
}