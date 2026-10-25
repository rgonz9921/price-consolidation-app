package com.rgonz.pricing.service;

import com.rgonz.pricing.dto.CreateProductRuleRequest;
import com.rgonz.pricing.dto.ProductRuleResponse;
import com.rgonz.pricing.dto.UpdateProductRuleRequest;
import java.util.List;

public interface ProductRuleService {

    List<ProductRuleResponse> findAll();

    ProductRuleResponse create(CreateProductRuleRequest request);

    ProductRuleResponse update(String sku, UpdateProductRuleRequest request);

    void delete(String sku);
}
