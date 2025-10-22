package com.larrydevincarter.optionscanner.utils;

import com.larrydevincarter.optionscanner.models.entities.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * Helper class for parsing financial reports from Alpha Vantage API responses.
 */
@Component
@Slf4j
public class FinancialReportParser {

    /**
     * Parses reports WITH fiscalDateEnding: IncomeStatement, CashFlow, BalanceSheet, Earnings
     */
    public static <T> List<T> parseReportsWithFiscalDate(
            List<Map<String, String>> reports,
            String symbol,
            String reportType,
            List<String> errorLog,
            Function<Map<String, String>, T> mapper) {

        Map<String, T> uniqueReports = new LinkedHashMap<>();

        for (Map<String, String> report : reports) {
            try {
                T entity = mapper.apply(report);
                if (entity != null) {
                    // CAST to access fiscalDateEnding (safe - all targets have it)
                    LocalDate fiscalDate = ((HasFiscalDate) entity).getFiscalDateEnding();
                    String key = fiscalDate + "|" + reportType;

                    if (uniqueReports.containsKey(key)) {
                        log.warn("Duplicate {} report for {} - {} ({})",
                                entity.getClass().getSimpleName(), symbol, fiscalDate, reportType);
                        errorLog.add("Duplicate " + entity.getClass().getSimpleName() +
                                " report for " + symbol + " - " + fiscalDate + " (" + reportType + ")");
                    }
                    uniqueReports.put(key, entity);
                }
            } catch (Exception e) {
                log.warn("Failed to parse {} report for {}: {}", reportType, symbol, e.getMessage());
                errorLog.add("Failed to parse " + reportType + " report for " + symbol + ": " + e.getMessage());
            }
        }
        return new ArrayList<>(uniqueReports.values());
    }

    /**
     * Parses dividends (uses exDividendDate)
     */
    public static List<Dividend> parseDividends(
            List<Map<String, String>> dividendData,
            String symbol,
            List<String> errorLog) {

        Map<String, Dividend> uniqueDividends = new LinkedHashMap<>();

        for (Map<String, String> data : dividendData) {
            try {
                Dividend dividend = new Dividend();
                dividend.setSymbol(symbol);

                LocalDate exDate = LocalDate.parse(data.get("ex_dividend_date"));
                dividend.setExDividendDate(exDate);

                parseOptionalDate(data, "declaration_date", dividend::setDeclarationDate);
                parseOptionalDate(data, "record_date", dividend::setRecordDate);
                parseOptionalDate(data, "payment_date", dividend::setPaymentDate);

                dividend.setAmount(parseDouble(data.get("amount"), errorLog));
                dividend.setLastUpdated(LocalDateTime.now());

                if (dividend.getAmount() == null) continue;

                String key = exDate.toString();
                if (uniqueDividends.containsKey(key)) {
                    log.warn("Duplicate dividend for {} - {}", symbol, exDate);
                    errorLog.add("Duplicate dividend for " + symbol + " - " + exDate);
                }
                uniqueDividends.put(key, dividend);

            } catch (Exception e) {
                log.warn("Failed to parse dividend for {}: {}", symbol, e.getMessage());
                errorLog.add("Failed to parse dividend for " + symbol + ": " + e.getMessage());
            }
        }
        return new ArrayList<>(uniqueDividends.values());
    }

    private static void parseOptionalDate(Map<String, String> data, String key, java.util.function.Consumer<LocalDate> setter) {
        String dateStr = data.get(key);
        if (dateStr != null && !"None".equals(dateStr)) {
            try {
                setter.accept(LocalDate.parse(dateStr));
            } catch (Exception ignored) {}
        }
    }

    public static Double parseDouble(String value, List<String> errorLog) {
        if (value == null || "None".equals(value)) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            String errorMsg = "Failed to parse value as Double: " + value;
            log.warn(errorMsg);
            errorLog.add(errorMsg);
            return null;
        }
    }
}