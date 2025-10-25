package com.larrydevincarter.optionscanner.models;

import com.larrydevincarter.optionscanner.models.entities.*;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Model class aggregating various financial reports for a company, including income statements,
 * earnings, balance sheets, cash flows, and assets.
 */
@AllArgsConstructor
@Data
public class FinancialReports {
    private final List<IncomeStatement> incomeStatements;
    private final List<Earnings> earnings;
    private final List<BalanceSheet> balanceSheets;
    private final List<CashFlow> cashFlows;
    private final List<Asset> assets;
}