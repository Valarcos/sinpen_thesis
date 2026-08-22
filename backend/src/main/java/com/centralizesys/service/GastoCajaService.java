package com.centralizesys.service;

import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.gastos.GastoCaja;
import com.centralizesys.model.gastos.GastoCajaAnulacionRequest;
import com.centralizesys.model.gastos.GastoCajaRequest;
import com.centralizesys.repository.GastoCajaRepository;
import com.centralizesys.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.centralizesys.model.auth.Usuario;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class GastoCajaService {

    private final GastoCajaRepository gastoCajaRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioRepository usuarioRepository;

    public GastoCajaService(GastoCajaRepository gastoCajaRepository,
                            AuditoriaService auditoriaService,
                            UsuarioRepository usuarioRepository) {
        this.gastoCajaRepository = gastoCajaRepository;
        this.auditoriaService = auditoriaService;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Long crearGasto(GastoCajaRequest request, Long authenticatedUserId) {
        GastoCaja gasto = new GastoCaja();
        gasto.setMonto(request.getMonto());
        gasto.setMotivo(request.getMotivo());
        gasto.setCategoria(request.getCategoria());

        gasto.setFechaGasto(request.getFechaGasto());
        gasto.setFechaRegistro(LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")));
        gasto.setRegistradoPorUsuarioId(authenticatedUserId);

        if (request.getPersonaInvolucrada() != null && !request.getPersonaInvolucrada().trim().isEmpty()) {
            gasto.setPersonaInvolucrada(request.getPersonaInvolucrada().trim());
        } else {
            String nombreUsuario = usuarioRepository.findById(authenticatedUserId)
                    .map(Usuario::getNombre)
                    .orElse("Usuario Desconocido");
            gasto.setPersonaInvolucrada(nombreUsuario);
        }

        Long id = gastoCajaRepository.save(gasto);

        auditoriaService.registrarAccion(authenticatedUserId, "CREAR_GASTO",
                "Gasto creado con ID: " + id + " por monto: $" + request.getMonto());

        return id;
    }

    @Transactional
    public void anularGasto(Long id, GastoCajaAnulacionRequest request, Long authenticatedUserId) {
        GastoCaja gasto = gastoCajaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto Caja", id));

        if (gasto.getAnulado() != null && gasto.getAnulado()) {
            throw new IllegalStateException("El gasto ya se encuentra anulado");
        }

        String razon = request != null ? request.getRazonAnulacion().trim() : "";
        gastoCajaRepository.anular(id, razon);

        String logMessage = "Gasto ID " + id + " anulado.";
        if (razon.isEmpty()) {
            logMessage = "[WARNING: Sin Razón Especificada] " + logMessage;
        } else {
            logMessage += " Razón: " + razon;
        }

        auditoriaService.registrarAccion(authenticatedUserId, "ANULAR_GASTO", logMessage);
    }

    /**
     * Returns a paginated list of Gastos, optionally filtered by date granularity.
     * The `page` parameter is 0-indexed. `size` controls the page size.
     * Date params (year, month, day) are all nullable; omitting them widens the filter.
     */
    public PageResponse<GastoCaja> obtenerGastos(Long page, Long size, Integer year, Integer month, Integer day) {
        size = Math.min(size, 100L);
        Long totalElements = gastoCajaRepository.countAll(year, month, day);
        Long totalPages    = (long) Math.ceil((double) totalElements / size);
        Long offset        = page * size;

        List<GastoCaja> content = gastoCajaRepository.findAll(size, offset, year, month, day);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
