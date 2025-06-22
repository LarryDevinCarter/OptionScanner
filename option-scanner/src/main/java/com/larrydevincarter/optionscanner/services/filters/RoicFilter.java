package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Data
@AllArgsConstructor
public class RoicFilter implements FinancialFilter<Object> {

    private double roicThreshold;
    private int years;
    private double defaultTaxRate;

    @Override
    public boolean appliesTo(String symbol, List<Object> reports) {
        List<IncomeStatement> incomeStatements = reports.stream()
                .filter(r -> r instanceof IncomeStatement)
                .map(r -> (IncomeStatement) r)
                .filter(s -> "annual".equals(s.getReportType()))
                .filter(s -> s.getOperatingIncome() != null && s.getIncomeBeforeTax() != null && s.getIncomeTaxExpense() != null)
                .sorted((s1, s2) -> s2.getFiscalDateEnding().compareTo(s1.getFiscalDateEnding()))
                .limit(years)
                .toList();

        List<BalanceSheet> balanceSheets = reports.stream()
                .filter(r -> r instanceof BalanceSheet)
                .map(r -> (BalanceSheet) r)
                .filter(s -> "annual".equals(s.getReportType()))
                .filter(s -> s.getTotalShareholderEquity() != null && s.getCashAndShortTermInvestments() != null)
                .sorted((s1, s2) -> s2.getFiscalDateEnding().compareTo(s1.getFiscalDateEnding()))
                .limit(years)
                .toList();

        if (incomeStatements.size() < years || balanceSheets.size() < years) {
            log.debug("Insufficient data for symbol {}: {} income statements, {} balance sheets",
                    symbol, incomeStatements.size(), balanceSheets.size());
            return false;
        }

        double totalRoic = 0.0;
        int validYears = 0;

        for (int i = 0; i < years; i++) {
            IncomeStatement income = incomeStatements.get(i);
            BalanceSheet balance = balanceSheets.get(i);

            if (income.getFiscalDateEnding().getYear() != balance.getFiscalDateEnding().getYear()) {
                log.debug("Fiscal year mismatch for symbol {}: income {}, balance {}",
                        symbol, income.getFiscalDateEnding(), balance.getFiscalDateEnding());
                continue;
            }

            double operatingIncome = income.getOperatingIncome();
            double taxRate = (income.getIncomeBeforeTax() != 0)
                    ? income.getIncomeTaxExpense() / income.getIncomeBeforeTax()
                    : defaultTaxRate;
            double nopat = operatingIncome * (1 - taxRate);

            double investedCapital = getInvestedCapital(balance);

            if (investedCapital <= 0) {
                log.debug("Invalid invested capital for symbol {} in year {}: {}",
                        symbol, income.getFiscalDateEnding().getYear(), investedCapital);
                continue;
            }

            double roic = (nopat / investedCapital) * 100;
            totalRoic += roic;
            validYears++;
            log.debug("ROIC for symbol {} in year {}: {}%", symbol, income.getFiscalDateEnding().getYear(), roic);
        }

        if (validYears == 0) {
            log.debug("No valid ROIC calculations for symbol {}", symbol);
            return false;
        }

        double averageRoic = totalRoic / validYears;
        log.debug("Average ROIC for symbol {} over {} years: {}% (threshold: {}%)",
                symbol, validYears, averageRoic, roicThreshold);
        return averageRoic > roicThreshold;
    }

    private static double getInvestedCapital(BalanceSheet balance) {
        double totalDebt = (balance.getShortLongTermDebtTotal() != null)
                ? balance.getShortLongTermDebtTotal()
                : (balance.getShortTermDebt() != null ? balance.getShortTermDebt() : 0.0)
                + (balance.getLongTermDebt() != null ? balance.getLongTermDebt() : 0.0);
        double equity = balance.getTotalShareholderEquity();
        double cash = (balance.getCashAndShortTermInvestments() != null)
                ? balance.getCashAndShortTermInvestments()
                : (balance.getCashAndCashEquivalentsAtCarryingValue() != null
                ? balance.getCashAndCashEquivalentsAtCarryingValue() : 0.0);
        return totalDebt + equity - cash;
    }

    @Override
    public String getName() {
        return "ROIC";
    }
}