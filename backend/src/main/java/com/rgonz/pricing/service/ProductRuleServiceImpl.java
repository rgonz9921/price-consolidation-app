package com.rgonz.pricing.service;

import com.rgonz.pricing.dto.CreateProductRuleRequest;
import com.rgonz.pricing.dto.ProductRuleResponse;
import com.rgonz.pricing.dto.UpdateProductRuleRequest;
import com.rgonz.pricing.exception.DuplicateSkuException;
import com.rgonz.pricing.exception.ProductRuleNotFoundException;
import com.rgonz.pricing.model.ProductRule;
import com.rgonz.pricing.repository.ProductRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductRuleServiceImpl implements ProductRuleService {

    private static final String UNIQUE_SKU_CONSTRAINT = "uk_product_rule_sku";

    private final ProductRuleRepository repository;
    private final PriceCacheService priceCache;
    private final Clock clock;

    public ProductRuleServiceImpl(ProductRuleRepository repository, PriceCacheService priceCache, Clock clock) {
        this.repository = repository;
        this.priceCache = priceCache;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductRuleResponse> findAll() {
        return repository.findAll(Sort.by("sku")).stream()
                .map(ProductRuleResponse::from)
                .toList();
    }

    @Override
    public ProductRuleResponse create(CreateProductRuleRequest request) {
        var rule = new ProductRule(
                request.sku(),
                request.name(),
                request.marginPercentage(),
                request.preferredProvider(),
                request.activeOrDefault(),
                now());
        try {
            return ProductRuleResponse.from(repository.saveAndFlush(rule));
        } catch (DataIntegrityViolationException e) {
            if (violatesUniqueSku(e)) {
                throw new DuplicateSkuException(request.sku());
            }
            throw e;
        }
    }

    @Override
    public ProductRuleResponse update(String sku, UpdateProductRuleRequest request) {
        var rule = findRule(sku);
        rule.update(request.name(), request.marginPercentage(), request.preferredProvider(), request.active(), now());
        repository.save(rule);
        priceCache.evict(sku);
        return ProductRuleResponse.from(rule);
    }

    @Override
    public void delete(String sku) {
        repository.delete(findRule(sku));
        priceCache.evict(sku);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private ProductRule findRule(String sku) {
        return repository.findBySku(sku).orElseThrow(() -> new ProductRuleNotFoundException(sku));
    }

    private static boolean violatesUniqueSku(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && UNIQUE_SKU_CONSTRAINT.equals(violation.getConstraintName());
    }
}
