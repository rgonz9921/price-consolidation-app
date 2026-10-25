package com.rgonz.pricing.exception;

public class SkuInactiveException extends RuntimeException {

    public SkuInactiveException(String sku) {
        super("La regla del SKU " + sku + " está inactiva");
    }
}
