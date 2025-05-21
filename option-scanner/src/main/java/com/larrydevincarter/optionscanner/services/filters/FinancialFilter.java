package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;

import java.util.List;

public interface FinancialFilter {

    boolean appliesToIncome(String symbol, List<IncomeStatement> statements);
    boolean appliesToEarnings(String symbol, List<Earnings> earnings);
    String getName();
}
