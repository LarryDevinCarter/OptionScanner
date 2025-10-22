package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.Dividend;
import com.larrydevincarter.optionscanner.repositories.base.BaseFinancialRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing Dividend entities.
 */
@Repository
public interface DividendRepository extends BaseFinancialRepository<Dividend> {

    /**
     * Finds symbols from the input list that lack data or have stale data before the given date.
     * Uses exDividendDate for Dividend staleness.
     *
     * @param date    the cutoff date for staleness
     * @param symbols the list of symbols to check
     * @return list of symbols needing updates
     */
    @Query("SELECT DISTINCT a.symbol " +
            "FROM Asset a " +
            "LEFT JOIN Dividend d ON a.symbol = d.symbol " +
            "WHERE a.symbol IN :symbols " +
            "AND (d.symbol IS NULL OR " +
            "     d.exDividendDate < :date " +
            "     AND d.exDividendDate = (" +
            "         SELECT MAX(d2.exDividendDate) " +
            "         FROM Dividend d2 " +
            "         WHERE d2.symbol = d.symbol" +
            "     )" +
            ")")
    List<String> findSymbolsNeedingUpdate(@Param("date") LocalDate date, @Param("symbols") List<String> symbols);

    /**
     * Finds symbols from the input list that have existing Dividend data.
     *
     * @param symbols the list of symbols to check
     * @return list of symbols with existing data
     */
    @Query("SELECT DISTINCT d.symbol " +
            "FROM Dividend d " +
            "WHERE d.symbol IN :symbols")
    List<String> findSymbolsWithData(@Param("symbols") List<String> symbols);
}