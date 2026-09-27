package com.farmacia.service;

import com.farmacia.model.Lote;
import com.farmacia.repository.LoteRepositorio;
import org.springframework.stereotype.Service;
import java.util.Optional;

import java.util.List;

@Service
public class LoteServicio {

    private final LoteRepositorio repositorio;

    public LoteServicio(LoteRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public List<Lote> listarPorMedicamento(Integer idMedicamento) {
        return repositorio.listarPorMedicamento(idMedicamento);
    }

    public Optional<Lote> buscarPorId(Integer idLote) {
        return repositorio.buscarPorId(idLote);
    }
}