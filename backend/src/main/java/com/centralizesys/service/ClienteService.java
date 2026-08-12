package com.centralizesys.service;

import com.centralizesys.model.client.ClienteResponse;
import com.centralizesys.repository.ClienteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    /**
     * Returns all active clients, ordered alphabetically.
     * The saldo_a_favor field is included so the frontend can
     * conditionally show the "Saldo a Favor" payment option.
     */
    public List<ClienteResponse> getAll() {
        return clienteRepository.findAll();
    }

    /**
     * Finds a client by their exact name for backward-compatibility lookups
     * (e.g., when auto-completing from the sales cart).
     */
    public java.util.Optional<ClienteResponse> findByNombre(String nombre) {
        return clienteRepository.findByNombre(nombre);
    }
}
