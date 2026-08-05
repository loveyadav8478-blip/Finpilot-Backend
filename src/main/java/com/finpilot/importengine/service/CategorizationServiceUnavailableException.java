package com.finpilot.importengine.service;

public class CategorizationServiceUnavailableException extends RuntimeException{
    public CategorizationServiceUnavailableException(String message, RuntimeException e){
        super(message, e);
    }
}
