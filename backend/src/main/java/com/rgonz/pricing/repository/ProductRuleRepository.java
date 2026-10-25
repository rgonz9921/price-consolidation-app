package com.rgonz.pricing.repository;

import com.rgonz.pricing.model.ProductRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRuleRepository extends JpaRepository<ProductRule, Long> {

    Optional<ProductRule> findBySku(String sku);
}
