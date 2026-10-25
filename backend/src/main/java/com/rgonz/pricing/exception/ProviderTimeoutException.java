package com.rgonz.pricing.exception;

import com.rgonz.pricing.model.Provider;

public class ProviderTimeoutException extends ProviderException {

    public ProviderTimeoutException(Provider provider, Throwable cause) {
        super(provider, "read timed out", cause);
    }
}
