package com.finpilot.auth.dto;

import lombok.Data;

@Data
public class RegisterRequest {

    private String email;
    private String password;

    public RegisterRequest() { }
}