package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeuda;
import com.centralizesys.model.debt.PagoDeudaRequest; // NEW
import com.centralizesys.model.enums.DebtStatus; // Using Enum
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.util.Constants;// Using Constants
import com.centralizesys.model.dto.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeudoresService {

    private final DeudoresRepository repository;
    private final AuditoriaService auditoriaService;
    private final com.centralizesys.repository.ClienteRepository clienteRepository;
    private final com.centralizesys.repository.MetodoPagoRepository metodoPagoRepository;

    public DeudoresService(DeudoresRepository repository, AuditoriaService auditoriaService,
                           com.centralizesys.repository.ClienteRepository clienteRepository,
                           com.centralizesys.repository.MetodoPagoRepository metodoPagoRepository) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
        this.clienteRepository = clienteRepository;
        this.metodoPagoRepository = metodoPagoRepository;
    }

    public List<DeudaResponse> getAll() {
        return repository.findAll();
    }

    public PageResponse<DeudaResponse> getPage(int page, int size) {
        int offset = page * size;
        List<DeudaResponse> deudas = repository.findPage(size, offset);
        long totalElements = repository.countAll();
        long totalPages = (long) Math.ceil((double) totalElements / size);

        return new PageResponse<>(deudas, (long) page, (long) size, totalElements, totalPages);
    }

    public DeudaResponse getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ERR_DEBT_NOT_FOUND, id));
    }

    @Transactional
    public DeudaResponse registrarPago(Long id, List<PagoDeudaRequest> pagos, Long usuarioId) {
        double totalPago = calculateTotalPayment(pagos);

        DeudaResponse deuda = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ERR_DEBT_NOT_FOUND, id));

        updateDebtBalanceAtomically(id, totalPago, deuda);
        processPaymentRecords(id, pagos, deuda, usuarioId);

        DeudaResponse updatedDeuda = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ERR_DEBT_NOT_FOUND, id));

        auditoriaService.registrarAccion(usuarioId, "PAGO_DEUDA",
                "Registrado pago de $" + totalPago + " (" + pagos.size() + " medios) para deuda ID " + id + " (" +
                        updatedDeuda.getClienteNombre() + ")");

        return updatedDeuda;
    }

    private double calculateTotalPayment(List<PagoDeudaRequest> pagos) {
        if (pagos == null || pagos.isEmpty()) {
            throw new BusinessRuleException("Debe ingresar al menos un pago.");
        }
        double totalPago = pagos.stream().mapToDouble(PagoDeudaRequest::getMontoPago).sum();
        if (totalPago <= 0) {
            throw new BusinessRuleException(Constants.ERR_PAYMENT_NEGATIVE);
        }
        return totalPago;
    }

    private void updateDebtBalanceAtomically(Long id, double totalPago, DeudaResponse deuda) {
        double rawNewBalance = deuda.getMontoDeuda() - totalPago;
        double saldoFinal = Math.round(rawNewBalance * 100.0) / 100.0;
        DebtStatus nuevoEstado = calculateStatus(saldoFinal, deuda.getMontoOriginal());

        int rowsAffected = repository.deductDeudaAtomic(id, totalPago, nuevoEstado.name());
        if (rowsAffected == 0) {
            throw new BusinessRuleException("Error de concurrencia al actualizar la deuda o monto de pago excesivo.");
        }
    }

    private void processPaymentRecords(Long id, List<PagoDeudaRequest> pagos, DeudaResponse deuda, Long usuarioId) {
        com.centralizesys.model.sales.MetodoPago saldoMethod = metodoPagoRepository.findByAcronimo("SALDO").orElse(null);
        Long saldoId = saldoMethod != null ? saldoMethod.getId() : null;

        for (PagoDeudaRequest pago : pagos) {
            if (pago.getMontoPago() > 0) {
                processSinglePaymentRecord(id, pago, deuda, saldoId, usuarioId);
            }
        }
    }

    private void processSinglePaymentRecord(Long id, PagoDeudaRequest pago, DeudaResponse deuda, Long saldoId, Long usuarioId) {
        if (saldoId != null && saldoId.equals(pago.getMetodoPagoId())) {
            if (deuda.getClienteId() == null) {
                throw new BusinessRuleException("El pago con Saldo a Favor requiere un cliente seleccionado.");
            }
            int rows = clienteRepository.deductSaldo(deuda.getClienteId(), pago.getMontoPago());
            if (rows == 0) {
                throw new BusinessRuleException("Saldo a favor insuficiente para el cliente.");
            }
        }
        repository.insertarPagoDeuda(id, pago.getMetodoPagoId(), pago.getMontoPago(), pago.getObservaciones(), usuarioId);
    }

    /**
     * Helper to determine status based purely on the remaining money.
     * This ensures the DB state never gets "stuck" in PAGADO if money is still
     * owed.
     */
    private DebtStatus calculateStatus(Double currentDebt, Double originalDebt) {
        // Floating point safety check (0.01 margin)
        if (currentDebt <= 0.01) {
            return DebtStatus.PAGADO;
        } else if (originalDebt != null && Math.abs(currentDebt - originalDebt) <= 0.01) {
            return DebtStatus.PENDIENTE;
        } else {
            return DebtStatus.PARCIAL;
        }
    }

    /**
     * Check if there are any active debts OR open pending sales.
     * Used by the frontend dashboard reminder badge for the CobrosYPedidos page.
     */
    public boolean hasActiveDebts() {
        return repository.hasActiveDebts();
    }

    /**
     * Get debts that have been pending/partial for more than X days.
     * Default to 15 days if not specified.
     */
    public List<DeudaResponse> getExpiredDebts() {
        // Hardcoded to 15 days for now, as implied by "missing api/deudores/expired"
        // and the "15-day reminder badge" context.
        return repository.findExpiredDebts(15);
    }

    public List<PagoDeuda> getPagos(Long id) {
        return repository.getPagosByDeudaId(id);
    }

    @Transactional
    public void anularPago(Long pagoId) {
        // 1. Fetch payment
        PagoDeuda pago = repository.findPagoById(pagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", pagoId));

        if (pago.getAnulado() != null && pago.getAnulado()) {
            throw new BusinessRuleException("El pago ya ha sido anulado.");
        }

        // 2. Fetch debt
        DeudaResponse deuda = repository.findById(pago.getDeudaId())
                .orElseThrow(() -> new ResourceNotFoundException("Deuda", pago.getDeudaId()));

        if ("ANULADA".equals(deuda.getEstado())) {
            throw new BusinessRuleException("No se puede anular un pago de una deuda ya anulada.");
        }

        // 3. Update DB Atomically
        repository.updatePagoAnulado(pagoId);
        double rawNewBalance = deuda.getMontoDeuda() + pago.getMonto();
        double saldoFinal = Math.round(rawNewBalance * 100.0) / 100.0;
        DebtStatus nuevoEstado = calculateStatus(saldoFinal, deuda.getMontoOriginal());

        int rowsAffected = repository.addDeudaAtomic(deuda.getId(), pago.getMonto(), nuevoEstado.name());
        if (rowsAffected == 0) {
            throw new BusinessRuleException("Error de concurrencia al restaurar la deuda.");
        }

        // 6. Audit
        Long currentUserId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
        auditoriaService.registrarAccion(currentUserId, "PAGO_DEUDA",
                "Anulación de pago ID " + pagoId + " por $" + pago.getMonto() + ". Deuda ID: " + deuda.getId());
    }
}