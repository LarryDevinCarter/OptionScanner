package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.repositories.AssetRepository;
import com.larrydevincarter.optionscanner.repositories.EarningsRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.EarningsService;
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

        Map<String, Earnings> uniqueEarnings = new LinkedHashMap<>();

        for (Map<String, String> report : reports) {

            Earnings earning = new Earnings();
            earning.setSymbol(symbol);
            LocalDate fiscalDate = LocalDate.parse(report.get("fiscalDateEnding"));
            earning.setFiscalDateEnding(fiscalDate);
            earning.setReportType(reportType);
            earning.setReportedEPS(parseDouble(report.get("reportedEPS"), errorLog));

            if ("quarterly".equals(reportType)) {

                earning.setReportedDate(report.get("reportedDate") !=null ? LocalDate.parse(report.get("reportedDate")) : null);
                earning.setEstimatedEPS(parseDouble(report.get("estimatedEPS"), errorLog));
                earning.setSurprise(parseDouble(report.get("surprise"), errorLog));
                earning.setSurprisePercentage(parseDouble(report.get("surprisePercentage"), errorLog));
            }
            earning.setLastUpdated(LocalDateTime.now());
            String key = fiscalDate + "|" + reportType;

            if (uniqueEarnings.containsKey(key)) {
                log.warn("Duplicate earnings report in API response for {} - {} ({})", symbol, fiscalDate, reportType);
                errorLog.add("Duplicate earnings report in API response: " + symbol + " - " + fiscalDate + " (" + reportType + ")");
            }
            uniqueEarnings.put(key, earning);
        }
        return new ArrayList<>(uniqueEarnings.values());
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
    public List<String> getSymbolsNeedingUpdate(List<String> symbols) {
        LocalDate date = LocalDate.now().minusDays(120);
        return earningsRepository.findSymbolsNeedingUpdate(date, symbols);
    }

    @Override
    public List<String> getSymbolsThatHaveStatements(List<String> symbols) {
        return earningsRepository.findSymbolsThatHaveStatements(symbols);
    }
}
