package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.Option;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionRepository extends JpaRepository<Option, String>, JpaSpecificationExecutor<Option> {

    @Modifying
    @Query("DELETE FROM Option o WHERE o.underlyingSymbol = :symbol")
    void deleteByUnderlyingSymbol(String symbol);

    List<Option> findByUnderlyingSymbol(String symbol);
}