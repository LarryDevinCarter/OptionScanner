package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.BalanceSheetRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.BalanceSheetService;
import com.larrydevincarter.optionscanner.utils.FinancialFilterUtils;
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
public class BalanceSheetServiceImpl implements BalanceSheetService {

    private final BalanceSheetRepository balanceSheetRepository;
    private final AssetRepository assetRepository;

    @Override
    @Transactional
    public void processReport(String symbol, Map<String, Object> response, List<String> errorLog) {
        balanceSheetRepository.deleteBySymbol(symbol);
        balanceSheetRepository.flush();
        log.info("Deleted existing balance sheets for symbol: {}", symbol);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> annualReports = (List<Map<String, String>>) response.get("annualReports");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> quarterlyReports = (List<Map<String, String>>) response.get("quarterlyReports");

        List<BalanceSheet> balanceSheets = new ArrayList<>();
        if (annualReports != null) {
            balanceSheets.addAll(parseReports(annualReports, symbol, "annual", errorLog));
        }
        if (quarterlyReports != null) {
            balanceSheets.addAll(parseReports(quarterlyReports, symbol, "quarterly", errorLog));
        }
        balanceSheetRepository.saveAll(balanceSheets);
        log.info("Stored {} balance sheets for symbol: {}", balanceSheets.size(), symbol);

        // New: Set sharesOutstanding in Asset from latest annual balance sheet
        List<BalanceSheet> allBalanceSheets = balanceSheetRepository.findBySymbol(symbol);
        List<BalanceSheet> annual = FinancialFilterUtils.getAnnualReports(
                allBalanceSheets,
                1,
                s -> s.getCommonStockSharesOutstanding() != null,
                FinancialFilterUtils.BALANCE_DATE_EXTRACTOR
        );
        if (!annual.isEmpty()) {
            assetRepository.findBySymbol(symbol).ifPresent(asset -> {
                asset.setSharesOutstanding(annual.get(0).getCommonStockSharesOutstanding());
                assetRepository.save(asset);
                log.info("Updated sharesOutstanding for asset: {}", symbol);
            });
        }
    }

    @Override
    public List<String> getSymbolsThatHaveData(List<String> symbols) {
        return balanceSheetRepository.findSymbolsWithData(symbols);
    }

    @Override
    public String getFunctionName() {
        return "BALANCE_SHEET";
    }

    @Override
    public String getReportDisplayName() {
        return "balance sheets";
    }

    private List<BalanceSheet> parseReports(List<Map<String, String>> reports, String symbol, String reportType, List<String> errorLog) {
        return FinancialReportParser.parseReportsWithFiscalDate(reports, symbol, reportType, errorLog, report -> {
            BalanceSheet balanceSheet = new BalanceSheet();
            balanceSheet.setSymbol(symbol);
            balanceSheet.setFiscalDateEnding(LocalDate.parse(report.get("fiscalDateEnding")));
            balanceSheet.setReportType(reportType);
            balanceSheet.setReportedCurrency(report.get("reportedCurrency"));
            balanceSheet.setTotalAssets(FinancialReportParser.parseDouble(report.get("totalAssets"), errorLog));
            balanceSheet.setTotalCurrentAssets(FinancialReportParser.parseDouble(report.get("totalCurrentAssets"), errorLog));
            balanceSheet.setCashAndCashEquivalentsAtCarryingValue(FinancialReportParser.parseDouble(report.get("cashAndCashEquivalentsAtCarryingValue"), errorLog));
            balanceSheet.setCashAndShortTermInvestments(FinancialReportParser.parseDouble(report.get("cashAndShortTermInvestments"), errorLog));
            balanceSheet.setInventory(FinancialReportParser.parseDouble(report.get("inventory"), errorLog));
            balanceSheet.setCurrentNetReceivables(FinancialReportParser.parseDouble(report.get("currentNetReceivables"), errorLog));
            balanceSheet.setTotalNonCurrentAssets(FinancialReportParser.parseDouble(report.get("totalNonCurrentAssets"), errorLog));
            balanceSheet.setIntangibleAssets(FinancialReportParser.parseDouble(report.get("intangibleAssets"), errorLog));
            balanceSheet.setIntangibleAssetsExcludingGoodwill(FinancialReportParser.parseDouble(report.get("intangibleAssetsExcludingGoodwill"), errorLog));
            balanceSheet.setGoodwill(FinancialReportParser.parseDouble(report.get("goodwill"), errorLog));
            balanceSheet.setTotalLiabilities(FinancialReportParser.parseDouble(report.get("totalLiabilities"), errorLog));
            balanceSheet.setTotalCurrentLiabilities(FinancialReportParser.parseDouble(report.get("totalCurrentLiabilities"), errorLog));
            balanceSheet.setCurrentAccountsPayable(FinancialReportParser.parseDouble(report.get("currentAccountsPayable"), errorLog));
            balanceSheet.setShortTermDebt(FinancialReportParser.parseDouble(report.get("shortTermDebt"), errorLog));
            balanceSheet.setTotalNonCurrentLiabilities(FinancialReportParser.parseDouble(report.get("totalNonCurrentLiabilities"), errorLog));
            balanceSheet.setCapitalLeaseObligations(FinancialReportParser.parseDouble(report.get("capitalLeaseObligations"), errorLog));
            balanceSheet.setLongTermDebt(FinancialReportParser.parseDouble(report.get("longTermDebt"), errorLog));
            balanceSheet.setCurrentLongTermDebt(FinancialReportParser.parseDouble(report.get("currentLongTermDebt"), errorLog));
            balanceSheet.setShortLongTermDebtTotal(FinancialReportParser.parseDouble(report.get("shortLongTermDebtTotal"), errorLog));
            balanceSheet.setOtherCurrentLiabilities(FinancialReportParser.parseDouble(report.get("otherCurrentLiabilities"), errorLog));
            balanceSheet.setOtherNonCurrentLiabilities(FinancialReportParser.parseDouble(report.get("otherNonCurrentLiabilities"), errorLog));
            balanceSheet.setTotalShareholderEquity(FinancialReportParser.parseDouble(report.get("totalShareholderEquity"), errorLog));
            balanceSheet.setRetainedEarnings(FinancialReportParser.parseDouble(report.get("retainedEarnings"), errorLog));
            balanceSheet.setCommonStock(FinancialReportParser.parseDouble(report.get("commonStock"), errorLog));
            balanceSheet.setCommonStockSharesOutstanding(FinancialReportParser.parseDouble(report.get("commonStockSharesOutstanding"), errorLog));
            balanceSheet.setLastUpdated(LocalDateTime.now());
            return balanceSheet;
        });
    }

    @Override
    public List<String> getSymbolsNeedingUpdate(List<String> symbols) {
        LocalDate date = LocalDate.now().minusDays(120);
        return balanceSheetRepository.findSymbolsNeedingUpdate(date, symbols);
    }
}