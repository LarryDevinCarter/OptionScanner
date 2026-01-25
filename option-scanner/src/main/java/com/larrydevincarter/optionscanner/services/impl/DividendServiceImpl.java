package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.entities.Dividend;
import com.larrydevincarter.optionscanner.repositories.DividendRepository;
import com.larrydevincarter.optionscanner.services.DividendService;
import com.larrydevincarter.optionscanner.utils.FinancialReportParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DividendServiceImpl implements DividendService {

    private final DividendRepository dividendRepository;

    @Override
    @Transactional
    public void processReport(String symbol, Map<String, Object> response, List<String> errorLog) {

        dividendRepository.deleteBySymbol(symbol);
        log.info("Deleted existing dividends for symbol: {}", symbol);
        List<Dividend> dividends = new ArrayList<>();
        List<Map<String, String>> dividendData = (List<Map<String, String>>) response.get("data");

        if (dividendData != null) {
            dividends.addAll(parseDividends(dividendData, symbol, errorLog));
        }
        dividendRepository.saveAll(dividends);
        log.info("Stored {} dividends for symbol: {}", dividends.size(), symbol);
    }

    @Override
    public List<String> getSymbolsThatHaveData(List<String> symbols) {
        return dividendRepository.findSymbolsWithData(symbols);
    }

    @Override
    public String getFunctionName() {
        return "DIVIDENDS";
    }

    @Override
    public String getReportDisplayName() {
        return "dividends";
    }

    private List<Dividend> parseDividends(List<Map<String, String>> dividendData, String symbol, List<String> errorLog) {
        return FinancialReportParser.parseDividends(dividendData, symbol, errorLog);
    }

    @Override
    public List<String> getSymbolsNeedingUpdate(List<String> symbols) {
        LocalDate date = LocalDate.now().minusDays(120);
        return dividendRepository.findSymbolsNeedingUpdate(date, symbols);
    }
}