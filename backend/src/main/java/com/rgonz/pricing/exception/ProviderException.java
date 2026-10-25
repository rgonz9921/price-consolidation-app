package com.rgonz.pricing.exception;

import com.rgonz.pricing.model.Provider;

public abstract class ProviderException extends RuntimeException {

    private final Provider provider;

    protected ProviderException(Provider provider, String message, Throwable cause) {
        super(provider + ": " + message, cause);
        this.provider = provider;
    }

    public Provider getProvider() {
        return provider;
    }
}
