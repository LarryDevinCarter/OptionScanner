package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.repositories.EarningsRepository;
import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
import com.larrydevincarter.optionscanner.services.EarningsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EarningsServiceImpl implements EarningsService {

    private final EarningsRepository earningsRepository;
    private final IncomeStatementRepository incomeStatementRepository;

    @Override
    @Transactional
    public void processEarnings(String symbol, Map<String, Object> response, List<String> errorLog) {

        earningsRepository.deleteBySymbol(symbol);
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
    public List<String> getSymbolsWithUpdatedIncomeStatements() {
        LocalDate oneHundredThirtyDaysAgo = LocalDate.now().minusDays(130);
        return incomeStatementRepository.findActiveTradableSymbolsNeedingUpdate(oneHundredThirtyDaysAgo);

//        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
//        return incomeStatementRepository.findSymbolsUpdatedToday(startOfDay);
        //TODO: rethink this method of selecting asset that need earnings updated.
    }
}
