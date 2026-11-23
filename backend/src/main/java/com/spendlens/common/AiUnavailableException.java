package com.spendlens.common;

/** Thrown when an AI-only feature is requested but Claude is not configured or failed. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
        super(message);
    }
}
