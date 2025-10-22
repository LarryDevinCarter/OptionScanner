package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.entities.Earnings;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.EarningsRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.EarningsService;
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
public class EarningsServiceImpl implements EarningsService {

    private final EarningsRepository earningsRepository;
    private final IncomeStatementRepository incomeStatementRepository;
    private final AssetRepository assetRepository;

    @Override
    @Transactional
    public void processEarnings(String symbol, Map<String, Object> response, List<String> errorLog) {

        earningsRepository.deleteBySymbol(symbol);
        earningsRepository.flush();
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
        earningsRepository.flush();
        log.info("Stored {} earnings records for symbol: {}", earningsList.size(), symbol);
    }

    private List<Earnings> parseEarnings(List<Map<String, String>> reports, String symbol, String reportType, List<String> errorLog) {

        return FinancialReportParser.parseReportsWithFiscalDate(reports, symbol, reportType, errorLog, report -> {
            Earnings earning = new Earnings();
            earning.setSymbol(symbol);
            earning.setFiscalDateEnding(LocalDate.parse(report.get("fiscalDateEnding")));
            earning.setReportType(reportType);
            earning.setReportedEPS(FinancialReportParser.parseDouble(report.get("reportedEPS"), errorLog));

            if ("quarterly".equals(reportType)) {
                earning.setReportedDate(report.get("reportedDate") != null ?
                        LocalDate.parse(report.get("reportedDate")) : null);
                earning.setEstimatedEPS(FinancialReportParser.parseDouble(report.get("estimatedEPS"), errorLog));
                earning.setSurprise(FinancialReportParser.parseDouble(report.get("surprise"), errorLog));
                earning.setSurprisePercentage(FinancialReportParser.parseDouble(report.get("surprisePercentage"), errorLog));
            }
            earning.setLastUpdated(LocalDateTime.now());
            return earning;
        });
    }

    @Override
    public List<String> getSymbolsNeedingUpdate(List<String> symbols) {
        LocalDate date = LocalDate.now().minusDays(120);
        return earningsRepository.findSymbolsNeedingUpdate(date, symbols);
    }

    @Override
    public List<String> getSymbolsThatHaveStatements(List<String> symbols) {
        return earningsRepository.findSymbolsWithData(symbols);
    }
}
