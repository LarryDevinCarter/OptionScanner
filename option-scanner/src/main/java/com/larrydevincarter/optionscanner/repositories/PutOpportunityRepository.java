package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.PutOpportunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PutOpportunityRepository extends JpaRepository<PutOpportunity, String> {

    List<PutOpportunity> findTop3ByOrderByPremiumDesc();
}
