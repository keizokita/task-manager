package com.example.taskmanager.exception;

public class WipLimitExceededException extends RuntimeException {
    public WipLimitExceededException(String message) {
        super(message);
    }
}
