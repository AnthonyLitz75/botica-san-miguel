package com.farmacia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccesoControlador {

    @GetMapping("/login")
    public String login() {
        return "acceso/login";
    }
}