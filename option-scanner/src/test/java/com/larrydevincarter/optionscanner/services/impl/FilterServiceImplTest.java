//package com.larrydevincarter.optionscanner.services.impl;
//
//import com.larrydevincarter.optionscanner.entities.Earnings;
//import com.larrydevincarter.optionscanner.entities.IncomeStatement;
//import com.larrydevincarter.optionscanner.repositories.AssetRepository;
//import com.larrydevincarter.optionscanner.repositories.IncomeStatementRepository;
//import com.larrydevincarter.optionscanner.services.filters.FinancialFilter;
//import com.larrydevincarter.optionscanner.services.filters.RevenueGrowthFilter;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//
//import java.time.LocalDate;
//import java.util.Collections;
//import java.util.List;
//
//import static org.mockito.Mockito.*;
//import static org.junit.jupiter.api.Assertions.*;
//
//class FilterServiceImplTest {
//
//    @InjectMocks
//    private FilterServiceImpl filterService;
//
//    @Mock
//    private AssetRepository assetRepository;
//
//    @Mock
//    private IncomeStatementRepository incomeStatementRepository;
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//        // Set default properties via reflection or constructor if needed; for simplicity, assume defaults are 5% and 5 years
//        filterService = new FilterServiceImpl(assetRepository, incomeStatementRepository);
//    }
//
//    @Test
//    void testGetSymbolsWithRevenueGrowth() {
//        // Mock active/tradable symbols
//        List<String> symbols = List.of("TEST1", "TEST2");
//        when(assetRepository.findActiveTradableSymbols()).thenReturn(symbols);
//
//        // Mock income statements
//        List<IncomeStatement> statements = List.of(
//                createStatement("TEST1", 2024, 134.01), // ~6% CAGR
//                createStatement("TEST1", 2023, 126.97),
//                createStatement("TEST1", 2022, 120.27),
//                createStatement("TEST1", 2021, 113.91),
//                createStatement("TEST1", 2020, 100.0),
//                createStatement("TEST2", 2024, 125.0),  // ~4.56% CAGR
//                createStatement("TEST2", 2023, 119.34),
//                createStatement("TEST2", 2022, 113.91),
//                createStatement("TEST2", 2021, 108.69),
//                createStatement("TEST2", 2020, 100.0)
//        );
//        when(incomeStatementRepository.findAll()).thenReturn(statements);
//
//        List<String> result = filterService.getSymbolsWithRevenueGrowth(5.0, 5);
//        assertEquals(1, result.size(), "Only TEST1 should pass with CAGR > 5%");
//        assertTrue(result.contains("TEST1"));
//        assertFalse(result.contains("TEST2"));
//    }
//
//    @Test
//    void testGetFilteredSymbolsWithNoFilters() {
//        List<String> symbols = List.of("TEST1", "TEST2");
//        when(assetRepository.findActiveTradableSymbols()).thenReturn(symbols);
//
//        List<String> result = filterService.getFilteredSymbols(Collections.emptyList());
//        assertEquals(2, result.size(), "No filters should return all active/tradable symbols");
//        assertTrue(result.containsAll(symbols));
//    }
//
//    @Test
//    void testGetFilteredSymbolsWithMultipleFilters() {
//        List<String> symbols = List.of("TEST1", "TEST2");
//        when(assetRepository.findActiveTradableSymbols()).thenReturn(symbols);
//
//        List<IncomeStatement> statements = List.of(
//                createStatement("TEST1", 2024, 134.01), // Passes revenue growth
//                createStatement("TEST1", 2023, 126.97),
//                createStatement("TEST1", 2022, 120.27),
//                createStatement("TEST1", 2021, 113.91),
//                createStatement("TEST1", 2020, 100.0),
//                createStatement("TEST2", 2024, 125.0),  // Fails revenue growth
//                createStatement("TEST2", 2023, 119.34),
//                createStatement("TEST2", 2022, 113.91),
//                createStatement("TEST2", 2021, 108.69),
//                createStatement("TEST2", 2020, 100.0)
//        );
//        when(incomeStatementRepository.findAll()).thenReturn(statements);
//
//        FinancialFilter revenueFilter = new RevenueGrowthFilter(5.0, 5);
//        // Mock another filter that always returns true for simplicity
//        FinancialFilter dummyFilter = new FinancialFilter() {
//            @Override
//            public boolean appliesToIncome(String symbol, List<IncomeStatement> statements) {
//                return true;
//            }
//
//            @Override
//            public boolean appliesToEarnings(String symbol, List<Earnings> earnings) {
//                return false;
//            }
//
//            @Override
//            public String getName() {
//                return "Dummy";
//            }
//        };
//
//        List<String> result = filterService.getFilteredSymbols(List.of(revenueFilter, dummyFilter));
//        assertEquals(1, result.size(), "Only TEST1 should pass both filters");
//        assertTrue(result.contains("TEST1"));
//    }
//
//    @Test
//    void testNoIncomeStatements() {
//        List<String> symbols = List.of("TEST1");
//        when(assetRepository.findActiveTradableSymbols()).thenReturn(symbols);
//        when(incomeStatementRepository.findAll()).thenReturn(Collections.emptyList());
//
//        List<String> result = filterService.getSymbolsWithRevenueGrowth(5.0, 5);
//        assertTrue(result.isEmpty(), "No income statements should result in no filtered symbols");
//    }
//
//    private IncomeStatement createStatement(String symbol, int year, double revenue) {
//        IncomeStatement statement = new IncomeStatement();
//        statement.setSymbol(symbol);
//        statement.setFiscalDateEnding(LocalDate.of(year, 12, 31));
//        statement.setReportType("annual");
//        statement.setTotalRevenue(revenue);
//        return statement;
//    }
//}