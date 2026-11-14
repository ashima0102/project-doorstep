package com.azentio.doorstep.ingest;

/** Unparseable or invalid event: retrying cannot help, so it goes straight to the DLT. */
public class MalformedEventException extends RuntimeException {
    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public MalformedEventException(String message) {
        super(message);
    }
}
