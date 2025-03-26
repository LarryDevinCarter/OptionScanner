package com.larrydevincarter.optionscanner.services;

import java.util.List;
import java.util.Map;

public interface IncomeStatementService {

    List<String> getSymbolsNeedingUpdate();

    void processIncomeStatements(String symbol, Map<String, Object> response, List<String> errorLog);

}
