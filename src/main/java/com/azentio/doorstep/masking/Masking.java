package com.azentio.doorstep.masking;

/** Masks identifiers for API responses, events and logs: only the last four characters stay visible. */
public final class Masking {

    private static final String MASK = "******";

    private Masking() {
    }

    public static String pan(String pan) {
        return lastFour(pan);
    }

    public static String phone(String phone) {
        return lastFour(phone);
    }

    private static String lastFour(String value) {
        if (value == null || value.length() <= 4) {
            return MASK;
        }
        return MASK + value.substring(value.length() - 4);
    }
}
