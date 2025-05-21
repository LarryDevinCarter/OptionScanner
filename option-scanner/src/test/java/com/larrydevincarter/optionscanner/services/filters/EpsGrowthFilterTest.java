package com.larrydevincarter.optionscanner.services.filters;

import com.larrydevincarter.optionscanner.entities.Earnings;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EpsGrowthFilterTest {

    @Test
    void testEpsGrowthFilter_ValidCagr() {
        Earnings e2024 = new Earnings();
        e2024.setReportType("annual");
        e2024.setFiscalDateEnding(LocalDate.of(2024, 12, 31));
        e2024.setReportedEPS(10.0);

        Earnings e2023 = new Earnings();
        e2023.setReportType("annual");
        e2023.setFiscalDateEnding(LocalDate.of(2023, 12, 31));
        e2023.setReportedEPS(8.0);

        Earnings e2022 = new Earnings();
        e2022.setReportType("annual");
        e2022.setFiscalDateEnding(LocalDate.of(2022, 12, 31));
        e2022.setReportedEPS(7.0);

        Earnings e2021 = new Earnings();
        e2021.setReportType("annual");
        e2021.setFiscalDateEnding(LocalDate.of(2021, 12, 31));
        e2021.setReportedEPS(6.0);

        Earnings e2020 = new Earnings();
        e2020.setReportType("annual");
        e2020.setFiscalDateEnding(LocalDate.of(2020, 12, 31));
        e2020.setReportedEPS(5.0);

        List<Earnings> earnings = List.of(e2024, e2023, e2022, e2021, e2020);
        EpsGrowthFilter filter = new EpsGrowthFilter(10.0, 5);
        assertTrue(filter.appliesToEarnings("TEST", earnings)); // CAGR ≈ 14.87%
    }

    @Test
    void testEpsGrowthFilter_InsufficientData() {
        Earnings e2024 = new Earnings();
        e2024.setReportType("annual");
        e2024.setFiscalDateEnding(LocalDate.of(2024, 12, 31));
        e2024.setReportedEPS(10.0);

        List<Earnings> earnings = List.of(e2024);
        EpsGrowthFilter filter = new EpsGrowthFilter(10.0, 5);
        assertFalse(filter.appliesToEarnings("TEST", earnings));
    }

    @Test
    void testEpsGrowthFilter_NegativeEps() {
        Earnings e2024 = new Earnings();
        e2024.setReportType("annual");
        e2024.setFiscalDateEnding(LocalDate.of(2024, 12, 31));
        e2024.setReportedEPS(-10.0);

        Earnings e2020 = new Earnings();
        e2020.setReportType("annual");
        e2020.setFiscalDateEnding(LocalDate.of(2020, 12, 31));
        e2020.setReportedEPS(5.0);

        List<Earnings> earnings = List.of(e2024, e2020);
        EpsGrowthFilter filter = new EpsGrowthFilter(10.0, 5);
        assertFalse(filter.appliesToEarnings("TEST", earnings));
    }
}