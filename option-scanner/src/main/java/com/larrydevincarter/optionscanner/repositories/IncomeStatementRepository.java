package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.IncomeStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncomeStatementRepository extends JpaRepository<IncomeStatement, Long> {

    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "LEFT JOIN IncomeStatement i ON a.symbol = i.symbol " +
            "WHERE a.status = 'active' AND a.tradable = true " +
            "AND (i.symbol IS NULL OR " +
            "     a.symbol NOT IN (" +
            "         SELECT i2.symbol " +
            "         FROM IncomeStatement i2 " +
            "         WHERE i2.fiscalDateEnding >= :date " +
            "         AND i2.symbol = a.symbol " +
            "     )" +
            ")")
    List<String> findActiveTradableSymbolsNeedingUpdate(LocalDate date);

    @Modifying
    @Query("DELETE FROM IncomeStatement i WHERE i.symbol = :symbol")
    void deleteBySymbol(String symbol);

    @Query("SELECT i FROM IncomeStatement i WHERE i.reportType = 'annual'")
    List<IncomeStatement> findAnnualStatements();

    @Query("SELECT DISTINCT i.symbol FROM IncomeStatement i WHERE i.lastUpdated >= :startOfDay")
    List<String> findSymbolsUpdatedToday(LocalDateTime startOfDay);

    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "LEFT JOIN IncomeStatement i ON a.symbol = i.symbol " +
            "WHERE a.symbol IN :symbols " +
            "AND (i.symbol IS NULL OR " +
            "     i.fiscalDateEnding < :date " +
            "     AND i.fiscalDateEnding = (" +
            "         SELECT MAX(i2.fiscalDateEnding) " +
            "         FROM IncomeStatement i2 " +
            "         WHERE i2.symbol = i.symbol" +
            "     )" +
            ")")
    List<String> findSymbolsNeedingUpdate(@Param("date") LocalDate date, @Param("symbols") List<String> symbols);

    @Query("SELECT DISTINCT i.symbol " +
            "FROM IncomeStatement i " +
            "WHERE i.symbol IN :symbols")
    List<String> findSymbolsThatHaveStatements(@Param("symbols") List<String> symbols);

    Optional<IncomeStatement> findTopBySymbolAndReportTypeOrderByFiscalDateEndingDesc(String symbol, String reportType);

    List<IncomeStatement> findBySymbol(String symbol);
}
