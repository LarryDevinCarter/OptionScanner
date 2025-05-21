package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.IncomeStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RevenueGrowthFilterTest {

    private RevenueGrowthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RevenueGrowthFilter(5.0, 5); // 5% CAGR over 5 years
    }

    @Test
    void testCagrAboveThreshold() {
        // 100 growing to 134.01 over 5 years ~= 6% CAGR
        List<IncomeStatement> statements = List.of(
                createStatement("TEST", 2024, 134.01),
                createStatement("TEST", 2023, 126.97),
                createStatement("TEST", 2022, 120.27),
                createStatement("TEST", 2021, 113.91),
                createStatement("TEST", 2020, 100.0)
        );
        assertTrue(filter.appliesToIncome("TEST", statements), "CAGR > 5% should pass");
    }

    @Test
    void testCagrBelowThreshold() {
        // 100 growing to 125 over 5 years ~= 4.56% CAGR
        List<IncomeStatement> statements = List.of(
                createStatement("TEST", 2024, 125.0),
                createStatement("TEST", 2023, 119.34),
                createStatement("TEST", 2022, 113.91),
                createStatement("TEST", 2021, 108.69),
                createStatement("TEST", 2020, 100.0)
        );
        assertFalse(filter.appliesToIncome("TEST", statements), "CAGR < 5% should fail");
    }

    @Test
    void testInsufficientData() {
        // Only 3 years of data
        List<IncomeStatement> statements = List.of(
                createStatement("TEST", 2024, 134.01),
                createStatement("TEST", 2023, 126.97),
                createStatement("TEST", 2022, 120.27)
        );
        assertFalse(filter.appliesToIncome("TEST", statements), "Insufficient years should fail");
    }

    @Test
    void testNoData() {
        List<IncomeStatement> statements = Collections.emptyList();
        assertFalse(filter.appliesToIncome("TEST", statements), "No data should fail");
    }

    @Test
    void testInvalidBeginningRevenue() {
        // Beginning revenue <= 0
        List<IncomeStatement> statements = List.of(
                createStatement("TEST", 2024, 134.01),
                createStatement("TEST", 2023, 126.97),
                createStatement("TEST", 2022, 120.27),
                createStatement("TEST", 2021, 113.91),
                createStatement("TEST", 2020, 0.0)
        );
        assertFalse(filter.appliesToIncome("TEST", statements), "Zero or negative beginning revenue should fail");
    }

    @Test
    void testNullRevenue() {
        // One year has null revenue
        IncomeStatement nullRevenue = createStatement("TEST", 2022, 120.27);
        nullRevenue.setTotalRevenue(null);
        List<IncomeStatement> statements = List.of(
                createStatement("TEST", 2024, 134.01),
                createStatement("TEST", 2023, 126.97),
                nullRevenue,
                createStatement("TEST", 2021, 113.91),
                createStatement("TEST", 2020, 100.0),
                createStatement("TEST", 2019, 95.0)
        );
        assertTrue(filter.appliesToIncome("TEST", statements), "Null revenue should be filtered out but still pass if enough data");
    }

    @Test
    void testGetName() {
        assertEquals("Revenue Growth", filter.getName(), "Filter name should match");
    }

    private IncomeStatement createStatement(String symbol, int year, double revenue) {
        IncomeStatement statement = new IncomeStatement();
        statement.setSymbol(symbol);
        statement.setFiscalDateEnding(LocalDate.of(year, 12, 31));
        statement.setReportType("annual");
        statement.setTotalRevenue(revenue);
        return statement;
    }
}