package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.Earnings;
import com.larrydevincarter.optionscanner.entities.IncomeStatement;

import java.util.List;

public interface FinancialFilter<T> {

    boolean appliesTo(String symbol, List<T> reports);
    String getName();
}
