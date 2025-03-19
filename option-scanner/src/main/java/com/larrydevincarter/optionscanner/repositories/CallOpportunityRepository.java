package com.larrydevincarter.optionscanner.repositories;

import com.larrydevincarter.optionscanner.entities.CallOpportunity;
import org.aspectj.weaver.ast.Call;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallOpportunityRepository extends JpaRepository<CallOpportunity, String> {

    List<CallOpportunity> findTop3ByOrderByPremiumDesc();
}
