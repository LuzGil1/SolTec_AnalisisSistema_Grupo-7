package com.example.soltec.service;

import com.example.soltec.dto.LoginRequest;
import com.example.soltec.dto.LoginResponse;
import com.example.soltec.dto.RegistroRequest;
import com.example.soltec.dto.RegistroResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    RegistroResponse registrar(RegistroRequest request);
}
