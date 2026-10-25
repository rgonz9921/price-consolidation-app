package com.rgonz.pricing.client;

import com.rgonz.pricing.exception.ProviderTimeoutException;
import java.util.function.Predicate;

public class InteractiveTimeoutPredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        return failure instanceof ProviderTimeoutException timeout && timeout.getMode() == QuoteMode.INTERACTIVE;
    }
}
