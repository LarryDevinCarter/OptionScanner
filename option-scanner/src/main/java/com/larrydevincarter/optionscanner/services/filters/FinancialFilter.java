package com.larrydevincarter.optionscanner.services.filters;


import com.larrydevincarter.optionscanner.models.FinancialReports;

public interface FinancialFilter {
    public static final double INVALID_RESULT = -1.0;
    boolean appliesTo(String symbol, FinancialReports reports);
    String getName();
}
