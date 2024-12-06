package com.github.iusmac.sevensim.telephony;

class InvalidSubscriptionIdException extends RuntimeException {
    public InvalidSubscriptionIdException(final int invalidSubId) {
        super("Invalid subscription ID (must be >= 0): " + invalidSubId);
    }
}
