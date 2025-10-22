package com.larrydevincarter.optionscanner.repositories.base;

import com.larrydevincarter.optionscanner.models.entities.base.BaseFinancialReport;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface StatementRepository<T extends BaseFinancialReport> extends BaseFinancialRepository<T> {

    /**
     * Finds symbols from the input list that lack data or have stale data before the given date.
     * Uses fiscalDateEnding for financial statements.
     *
     * @param date    the cutoff date for staleness
     * @param symbols the list of symbols to check
     * @return list of symbols needing updates
     */
    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "LEFT JOIN #{#entityName} e ON a.symbol = e.symbol " +
            "WHERE a.symbol IN :symbols " +
            "AND (e.symbol IS NULL OR " +
            "     e.fiscalDateEnding < :date " +
            "     AND e.fiscalDateEnding = (" +
            "         SELECT MAX(e2.fiscalDateEnding) " +
            "         FROM #{#entityName} e2 " +
            "         WHERE e2.symbol = e.symbol" +
            "     )" +
            ")")
    List<String> findSymbolsNeedingUpdate(@Param("date") LocalDate date, @Param("symbols") List<String> symbols);

    /**
     * Finds symbols from the input list that have existing data.
     *
     * @param symbols the list of symbols to check
     * @return list of symbols with existing data
     */
    @Query("SELECT DISTINCT e.symbol " +
            "FROM #{#entityName} e " +
            "WHERE e.symbol IN :symbols")
    List<String> findSymbolsWithData(@Param("symbols") List<String> symbols);

    /**
     * Finds the most recent record for a given symbol and report type, ordered descending by fiscalDateEnding.
     *
     * @param symbol     the stock symbol
     * @param reportType the type of report (e.g., quarterly, annual)
     * @return the most recent record, if present
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.symbol = :symbol AND e.reportType = :reportType AND e.fiscalDateEnding = (SELECT MAX(e2.fiscalDateEnding) FROM #{#entityName} e2 WHERE e2.symbol = :symbol AND e2.reportType = :reportType)")
    Optional<T> findTopBySymbolAndReportTypeOrderByFiscalDateEndingDesc(@Param("symbol") String symbol, @Param("reportType") String reportType);
}