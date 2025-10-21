package com.larrydevincarter.optionscanner.services.filters;


import com.larrydevincarter.optionscanner.models.FinancialReports;

public interface FinancialFilter {

    boolean appliesTo(String symbol, FinancialReports reports);
    String getName();
}
