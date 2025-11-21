package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository for managing Option entities.
 */
@Repository
public interface OptionRepository extends JpaRepository<Option, String>, JpaSpecificationExecutor<Option> {

    /**
     * Deletes all Option records for the given underlying symbol.
     *
     * @param symbol the underlying symbol to delete records for
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Option o WHERE o.underlyingSymbol = :symbol")
    void deleteByUnderlyingSymbol(String symbol);

    @Modifying
    @Transactional
    @Query("DELETE FROM Option o WHERE o.underlyingSymbol = :symbol AND o.optionType = :type")
    void deleteByUnderlyingSymbolAndOptionType(@Param("symbol") String symbol, @Param("type") String type);

    List<Option> findByUnderlyingSymbolAndOptionTypeOrderByYieldDesc(String underlyingSymbol, String optionType);
}