package com.srelab.sandbox.model;

/**
 * Thrown when a StartRunRequest is missing required fields (target image,
 * or any code-import source). Kept distinct from generic IllegalArgumentException
 * so RunController can map it to a 400 response with a clear message instead
 * of letting the request silently proceed and fail deep inside Docker/Testcontainers
 * with a confusing NPE, or -- worse -- silently "succeed" by deploying the
 * target image completely unmodified when the caller actually intended to
 * test their own code.
 */
public class InvalidRunRequestException extends RuntimeException {
    public InvalidRunRequestException(String message) {
        super(message);
    }
}
