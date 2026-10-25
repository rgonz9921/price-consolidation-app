package com.rgonz.pricing.controller;

import com.rgonz.pricing.dto.PriceResponse;
import com.rgonz.pricing.service.PriceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    private final PriceQueryService priceQueryService;

    public PriceController(PriceQueryService priceQueryService) {
        this.priceQueryService = priceQueryService;
    }

    @GetMapping
    public PriceResponse getPrice(@RequestParam String sku) {
        return priceQueryService.getPrice(sku);
    }
}
