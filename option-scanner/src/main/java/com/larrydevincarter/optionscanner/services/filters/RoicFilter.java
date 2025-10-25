package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.models.FinancialReports;
import com.larrydevincarter.optionscanner.models.entities.BalanceSheet;
import com.larrydevincarter.optionscanner.models.entities.IncomeStatement;
import com.larrydevincarter.optionscanner.utils.FinancialFilterUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * A filter that evaluates companies based on their average Return on Invested Capital (ROIC).
 */
@Slf4j
@Data
@AllArgsConstructor
public class RoicFilter implements FinancialFilter {

    private double roicThreshold;
    private int years;
    private double defaultTaxRate;

    /**
     * Checks if the company's average ROIC meets the specified threshold.
     *
     * @param symbol  the stock symbol
     * @param reports the financial reports containing income statements and balance sheets
     * @return true if the average ROIC exceeds the threshold, false otherwise
     */
    @Override
    public boolean appliesTo(String symbol, FinancialReports reports) {
        double averageRoic = calculateAverageRoic(symbol, reports);
        if (averageRoic < 0) {
            return false;
        }
        log.debug("Average ROIC for symbol {} over {} years: {}% (threshold: {}%)",
                symbol, years, averageRoic, roicThreshold);
        return averageRoic > roicThreshold;
    }

    /**
     * Calculates the average ROIC over the specified number of years.
     *
     * @param symbol  the stock symbol
     * @param reports the financial reports containing income statements and balance sheets
     * @return the average ROIC percentage, or INVALID_RESULT if calculation is not possible
     */
    public double calculateAverageRoic(String symbol, FinancialReports reports) {
        List<IncomeStatement> incomeStatements = FinancialFilterUtils.getAnnualReports(
                reports.getIncomeStatements(), years,
                s -> s.getOperatingIncome() != null && s.getIncomeBeforeTax() != null && s.getIncomeTaxExpense() != null,
                FinancialFilterUtils.INCOME_DATE_EXTRACTOR
        );

        List<BalanceSheet> balanceSheets = FinancialFilterUtils.getAnnualReports(
                reports.getBalanceSheets(), years,
                s -> s.getTotalShareholderEquity() != null && s.getCashAndShortTermInvestments() != null,
                FinancialFilterUtils.BALANCE_DATE_EXTRACTOR
        );

        if (incomeStatements.size() < years || balanceSheets.size() < years) {
            return INVALID_RESULT;
        }

        double totalRoic = 0.0;
        int validYears = 0;

        for (int i = 0; i < years; i++) {
            IncomeStatement income = incomeStatements.get(i);
            BalanceSheet balance = balanceSheets.get(i);

            if (income.getFiscalDateEnding().getYear() != balance.getFiscalDateEnding().getYear()) {
                continue;
            }

            double operatingIncome = income.getOperatingIncome();
            double taxRate = (income.getIncomeBeforeTax() != 0)
                    ? income.getIncomeTaxExpense() / income.getIncomeBeforeTax()
                    : defaultTaxRate;
            double nopat = operatingIncome * (1 - taxRate);

            double investedCapital = getInvestedCapital(balance);

            if (investedCapital <= 0) {
                continue;
            }

            double roic = (nopat / investedCapital) * 100;
            totalRoic += roic;
            validYears++;
        }

        if (validYears == 0) {
            return INVALID_RESULT;
        }

        return totalRoic / validYears;
    }

    /**
     * Calculates the invested capital for a given balance sheet.
     *
     * @param balance the balance sheet
     * @return the invested capital (total debt + equity - cash)
     */
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

    /**
     * Returns the name of the filter.
     *
     * @return the filter name, "ROIC"
     */
    @Override
    public String getName() {
        return "ROIC";
    }
}