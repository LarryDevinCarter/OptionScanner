package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.IncomeStatement;

import java.util.List;

public interface FinancialFilter {

    boolean appliesTo(String symbol, List<IncomeStatement> statements);

    String getName();
}
