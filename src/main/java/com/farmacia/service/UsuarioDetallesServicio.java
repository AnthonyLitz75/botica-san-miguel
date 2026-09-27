package com.farmacia.service;

import com.farmacia.model.Usuario;
import com.farmacia.repository.UsuarioRepositorio;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetallesServicio implements UserDetailsService {

    private final UsuarioRepositorio repositorio;

    public UsuarioDetallesServicio(UsuarioRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public UserDetails loadUserByUsername(String nombreUsuario)
            throws UsernameNotFoundException {

        Usuario usuario = repositorio.buscarPorNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado"));

        return User.withUsername(usuario.nombreUsuario())
                .password(usuario.claveHash())
                .roles(usuario.rol())
                .disabled(!usuario.activo())
                .build();
    }
}