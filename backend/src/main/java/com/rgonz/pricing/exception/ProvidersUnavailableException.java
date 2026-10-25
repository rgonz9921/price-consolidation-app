package com.rgonz.pricing.exception;

public class ProvidersUnavailableException extends RuntimeException {

    public ProvidersUnavailableException(String sku) {
        super("Ningún proveedor respondió para el SKU " + sku + " y no hay un precio reciente en caché");
    }
}
