package com.rgonz.pricing.exception;

import com.rgonz.pricing.model.Provider;

public class ProviderUnavailableException extends ProviderException {

    public ProviderUnavailableException(Provider provider, String reason) {
        super(provider, reason, null);
    }

    public ProviderUnavailableException(Provider provider, String reason, Throwable cause) {
        super(provider, reason, cause);
    }
}
