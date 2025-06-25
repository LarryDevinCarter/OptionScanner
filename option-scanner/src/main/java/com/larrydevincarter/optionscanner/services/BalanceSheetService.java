package com.larrydevincarter.optionscanner.services;

import java.util.List;
import java.util.Map;

public interface BalanceSheetService {

    List<String> getSymbolsNeedingUpdate(List<String> symbols);

    void processBalanceSheets(String symbol, Map<String, Object> responseBody, List<String> errorLog);

    List<String> getSymbolsThatHaveStatements(List<String> symbols);
}
