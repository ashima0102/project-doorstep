package com.azentio.doorstep.signals;

public enum SignalType {
    /** key = PAN, member = applicationId|dob|normalisedName */
    PAN_IDENTITY,
    /** key = phone, member = hashed PAN */
    PHONE_PANS,
    /** key = deviceId, member = hashed PAN */
    DEVICE_PANS
}
