package com.rgonz.pricing.exception;

import com.rgonz.pricing.client.QuoteMode;
import com.rgonz.pricing.model.Provider;

public class ProviderTimeoutException extends ProviderException {

    private final QuoteMode mode;

    public ProviderTimeoutException(Provider provider, QuoteMode mode, Throwable cause) {
        super(provider, mode.name().toLowerCase() + " read timed out", cause);
        this.mode = mode;
    }

    public QuoteMode getMode() {
        return mode;
    }
}
