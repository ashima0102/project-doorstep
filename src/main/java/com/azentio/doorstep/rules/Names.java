package com.azentio.doorstep.rules;

import java.util.Locale;

public final class Names {

    private Names() {
    }

    /** Case- and whitespace-insensitive form: trims, collapses inner whitespace and upper-cases. */
    public static String normalise(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
