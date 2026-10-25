package com.rgonz.pricing.exception;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("Ya existe una regla para el SKU " + sku);
    }
}
