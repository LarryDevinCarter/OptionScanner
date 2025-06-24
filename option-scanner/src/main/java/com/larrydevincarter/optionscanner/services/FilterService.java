package com.larrydevincarter.optionscanner.services;

import com.larrydevincarter.optionscanner.services.filters.FinancialFilter;

import java.util.List;

public interface FilterService {

    List<String> getSymbolsWithRevenueGrowth(double cagrThreshold, int years);
    List<String> getSymbolsWithEpsGrowth(double cagrThreshold, int years);
    List<String> getSymbolsWithRoic(double roicThreshold, int years, double defaultTaxRate);
    List<String> getSymbolsWithDebtToEquity(double debtToEquityThreshold);
    List<String> getFilteredSymbols(List<FinancialFilter<?>> filters);
}
