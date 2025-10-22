package com.larrydevincarter.optionscanner.services.impl;

import com.larrydevincarter.optionscanner.models.entities.Dividend;
import com.larrydevincarter.optionscanner.repositories.DividendRepository;
import com.larrydevincarter.optionscanner.services.DividendService;
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
    public void processDividends(String symbol, Map<String, Object> response, List<String> errorLog) {

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

    private List<Dividend> parseDividends(List<Map<String, String>> dividendData, String symbol, List<String> errorLog) {

        Map<String, Dividend> uniqueDividends = new LinkedHashMap<>();

        for (Map<String, String> data : dividendData) {
            Dividend dividend = new Dividend();
            dividend.setSymbol(symbol);

            try {
                dividend.setExDividendDate(LocalDate.parse(data.get("ex_dividend_date")));
            } catch (Exception e) {
                log.warn("Failed to parse ex_dividend_date for {}: {}", symbol, data.get("ex_dividend_date"));
                errorLog.add("Failed to parse ex_dividend_date for " + symbol + ": " + data.get("ex_dividend_date"));
                continue;
            }

            try {
                String declarationDate = data.get("declaration_date");
                dividend.setDeclarationDate(declarationDate != null && !declarationDate.equals("None") ? LocalDate.parse(declarationDate) : null);
                String recordDate = data.get("record_date");
                dividend.setRecordDate(recordDate != null && !recordDate.equals("None") ? LocalDate.parse(recordDate) : null);
                String paymentDate = data.get("payment_date");
                dividend.setPaymentDate(paymentDate != null && !paymentDate.equals("None") ? LocalDate.parse(paymentDate) : null);
            } catch (Exception e) {
                log.warn("Failed to parse date fields for {}: {}", symbol, e.getMessage());
                errorLog.add("Failed to parse date fields for " + symbol + ": " + e.getMessage());
            }

            try {
                dividend.setAmount(Double.parseDouble(data.get("amount")));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse amount for {}: {}", symbol, data.get("amount"));
                errorLog.add("Failed to parse amount for " + symbol + ": " + data.get("amount"));
                continue;
            }
            dividend.setLastUpdated(LocalDateTime.now());
            String key = dividend.getExDividendDate().toString();

            if (uniqueDividends.containsKey(key)) {
                log.warn("Duplicate dividend report for {} - {}", symbol, dividend.getExDividendDate());
                errorLog.add("Duplicate dividend report for " + symbol + " - " + dividend.getExDividendDate());
            }
            uniqueDividends.put(key, dividend);
        }
        return new ArrayList<>(uniqueDividends.values());
    }

    @Override
    public List<String> getSymbolsNeedingUpdate(List<String> symbols) {
        LocalDate date = LocalDate.now().minusDays(120);
        return dividendRepository.findSymbolsNeedingUpdate(date, symbols);
    }

    @Override
    public List<String> getSymbolsThatHaveDividends(List<String> symbols) {
        return dividendRepository.findSymbolsWithData(symbols);
    }
}