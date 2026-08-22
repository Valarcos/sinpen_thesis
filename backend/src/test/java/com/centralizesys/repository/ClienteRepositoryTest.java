package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.client.Cliente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    @DisplayName("updateNombre - Updates name and audit fields successfully")
    void updateNombre_UpdatesNameSuccessfully() {
        // Arrange
        Long userId = createTestUser();
        Cliente cliente = new Cliente();
        cliente.setNombre("Old Name");
        cliente.setTelefono("123");
        cliente.setDni("123");
        cliente.setActivo(true);
        clienteRepository.save(cliente, userId);

        Long clienteId = cliente.getId();

        // Act
        clienteRepository.updateNombre(clienteId, "New Name", userId);

        // Assert
        Optional<Cliente> updatedOpt = clienteRepository.findById(clienteId);
        assertThat(updatedOpt).isPresent();
        Cliente updated = updatedOpt.get();
        assertThat(updated.getNombre()).isEqualTo("New Name");
        assertThat(updated.getActualizadoPor()).isEqualTo(userId);
    }
}
