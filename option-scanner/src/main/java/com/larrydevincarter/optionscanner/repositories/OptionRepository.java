package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.models.entities.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
    @Query("DELETE FROM Option o WHERE o.underlyingSymbol = :symbol")
    void deleteByUnderlyingSymbol(String symbol);
}