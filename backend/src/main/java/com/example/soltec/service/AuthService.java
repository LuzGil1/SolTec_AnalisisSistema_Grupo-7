package com.example.soltec.service;

import com.example.soltec.dto.LoginRequest;
import com.example.soltec.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
