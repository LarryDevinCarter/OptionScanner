package com.larrydevincarter.optionscanner.utils;

import com.larrydevincarter.optionscanner.models.entities.*;
import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@UtilityClass
public class FinancialFilterUtils {

    public static final String ANNUAL_REPORT_TYPE = "annual";

    /**
     * Generic method for single-entity annual report filtering + sorting
     */
    public static <T> List<T> getAnnualReports(
            List<T> reports,
            int maxYears,
            Predicate<T> nullCheck,
            java.util.function.Function<T, LocalDate> dateExtractor) {

        return reports.stream()
                .filter(r -> ANNUAL_REPORT_TYPE.equals(getReportType(r)))
                .filter(nullCheck)
                .sorted((a, b) -> dateExtractor.apply(b).compareTo(dateExtractor.apply(a)))
                .limit(maxYears)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Asset-specific filtering (no annual check, no date sorting)
     */
    public static List<Asset> getValidAssets(List<Asset> assets) {
        return assets.stream()
                .filter(a -> a.getCurrentPrice() != null)
                .toList();
    }

    private static String getReportType(Object report) {
        if (report instanceof IncomeStatement s) return s.getReportType();
        if (report instanceof Earnings s) return s.getReportType();
        if (report instanceof BalanceSheet s) return s.getReportType();
        if (report instanceof CashFlow s) return s.getReportType();
        return null;
    }

    public static java.util.function.Function<IncomeStatement, LocalDate> INCOME_DATE_EXTRACTOR =
            IncomeStatement::getFiscalDateEnding;
    public static java.util.function.Function<Earnings, LocalDate> EARNINGS_DATE_EXTRACTOR =
            Earnings::getFiscalDateEnding;
    public static java.util.function.Function<BalanceSheet, LocalDate> BALANCE_DATE_EXTRACTOR =
            BalanceSheet::getFiscalDateEnding;
    public static java.util.function.Function<CashFlow, LocalDate> CASHFLOW_DATE_EXTRACTOR =
            CashFlow::getFiscalDateEnding;
}