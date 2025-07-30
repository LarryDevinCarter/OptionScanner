package com.larrydevincarter.optionscanner.repositories.base;

import com.larrydevincarter.optionscanner.entities.base.BaseFinancialReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Generic repository for financial report entities extending BaseFinancialReport.
 * Provides common queries for identifying symbols needing updates or having existing data.
 *
 * @param <T> the entity type extending BaseFinancialReport
 */
@NoRepositoryBean
public interface BaseFinancialRepository<T extends BaseFinancialReport> extends JpaRepository<T, Long> {

    /**
     * Deletes all records for the given symbol.
     *
     * @param symbol the symbol to delete records for
     */
    @Modifying
    @Query("DELETE FROM #{#entityName} e WHERE e.symbol = :symbol")
    void deleteBySymbol(@Param("symbol") String symbol);

    /**
     * Finds all records for the given symbol.
     *
     * @param symbol the symbol to query
     * @return list of records for the symbol
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.symbol = :symbol")
    List<T> findBySymbol(@Param("symbol") String symbol);
}