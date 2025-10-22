package com.larrydevincarter.optionscanner.utils;

import com.larrydevincarter.optionscanner.models.entities.Option;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public class OptionSpecifications {

    public static Specification<Option> isPut() {
        return (root, query, cb) -> cb.equal(root.get("optionType"), "put");
    }

    public static Specification<Option> yieldGte(double yieldValue) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("yield"), yieldValue);
    }

    public static Specification<Option> adjustedPeGte(double pe) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("adjustedPe"), pe);
    }

    public static Specification<Option> underlyingIn(List<String> symbols) {
        return (root, query, cb) -> root.get("underlyingSymbol").in(symbols);
    }

    public static Specification<Option> strikeLte(double strike) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("strike"), strike);
    }
}