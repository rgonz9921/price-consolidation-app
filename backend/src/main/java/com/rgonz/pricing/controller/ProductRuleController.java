package com.rgonz.pricing.controller;

import com.rgonz.pricing.dto.CreateProductRuleRequest;
import com.rgonz.pricing.dto.ProductRuleResponse;
import com.rgonz.pricing.dto.UpdateProductRuleRequest;
import com.rgonz.pricing.service.ProductRuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/product-rules")
public class ProductRuleController {

    private final ProductRuleService productRuleService;

    public ProductRuleController(ProductRuleService productRuleService) {
        this.productRuleService = productRuleService;
    }

    @GetMapping
    public List<ProductRuleResponse> list() {
        return productRuleService.findAll();
    }

    @PostMapping
    public ResponseEntity<ProductRuleResponse> create(@Valid @RequestBody CreateProductRuleRequest request) {
        var created = productRuleService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sku}")
                .buildAndExpand(created.sku())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{sku}")
    public ProductRuleResponse update(@PathVariable String sku, @Valid @RequestBody UpdateProductRuleRequest request) {
        return productRuleService.update(sku, request);
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<Void> delete(@PathVariable String sku) {
        productRuleService.delete(sku);
        return ResponseEntity.noContent().build();
    }
}
