package com.larrydevincarter.optionscanner.services;

import java.util.List;
import java.util.Map;

public interface EarningsService {

    List<String> getSymbolsWithUpdatedIncomeStatements();

    void processEarnings(String symbol, Map<String, Object> response, List<String> errorLog);
}
