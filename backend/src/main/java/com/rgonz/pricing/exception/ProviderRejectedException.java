package com.rgonz.pricing.exception;

import com.rgonz.pricing.model.Provider;

public class ProviderRejectedException extends ProviderException {

    public ProviderRejectedException(Provider provider, String reason) {
        super(provider, reason, null);
    }
}
