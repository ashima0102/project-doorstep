package com.azentio.doorstep.rules;

public enum Decision {
    PASS, REVIEW, REJECT;

    public boolean opensCase() {
        return this != PASS;
    }
}
