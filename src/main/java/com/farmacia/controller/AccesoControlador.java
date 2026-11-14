package com.farmacia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccesoControlador {

    /** Inicio: lleva a la pantalla principal (spec 013). Sin sesión, la seguridad redirige al login. */
    @GetMapping("/")
    public String inicio() {
        return "redirect:/medicamentos";
    }

    @GetMapping("/login")
    public String login() {
        return "acceso/login";
    }
}