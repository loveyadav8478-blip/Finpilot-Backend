package com.finpilot.auth.dto;


import lombok.Data;

@Data
public class LoginRequest {

    String email;
    String password;

    public LoginRequest(){}
}
