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
import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.repository.AlertaChequeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeudoresService {

    private final DeudoresRepository repository;
    private final AuditoriaService auditoriaService;
    private final com.centralizesys.repository.ClienteRepository clienteRepository;
    private final com.centralizesys.repository.MetodoPagoRepository metodoPagoRepository;
    private final AlertaChequeRepository alertaChequeRepository;

    public DeudoresService(DeudoresRepository repository, AuditoriaService auditoriaService,
                           com.centralizesys.repository.ClienteRepository clienteRepository,
                           com.centralizesys.repository.MetodoPagoRepository metodoPagoRepository,
                           AlertaChequeRepository alertaChequeRepository) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
        this.clienteRepository = clienteRepository;
        this.metodoPagoRepository = metodoPagoRepository;
        this.alertaChequeRepository = alertaChequeRepository;
    }

    public List<DeudaResponse> getAll() {
        return repository.findAll();
    }

    public PageResponse<DeudaResponse> getPage(int page, int size) {
        size = Math.min(size, 100);
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

    public DeudaResponse getByVentaId(Long ventaId) {
        return repository.findByVentaId(ventaId)
                .orElseThrow(() -> new ResourceNotFoundException("Deuda de Venta", ventaId));
    }

    @Transactional
    public DeudaResponse registrarPago(Long id, List<PagoDeudaRequest> pagos, Long usuarioId) {
        double totalPago = calculateTotalPayment(pagos);

        DeudaResponse deuda = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ERR_DEBT_NOT_FOUND, id));

        if (totalPago > deuda.getMontoDeuda() + 0.01) {
            throw new BusinessRuleException(String.format("El monto del pago ($%.2f) supera la deuda pendiente ($%.2f).", totalPago, deuda.getMontoDeuda()));
        }

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
        // ONLY sum cash/transfers. Cheques do NOT reduce the debt instantly.
        double totalPago = pagos.stream()
                .filter(p -> p.getFechaCobro() == null)
                .mapToDouble(PagoDeudaRequest::getMontoPago)
                .sum();

        // Ensure at least one payment is > 0 (even if it's a cheque, but here totalPago is cash)
        double totalIncludingCheques = pagos.stream().mapToDouble(PagoDeudaRequest::getMontoPago).sum();
        if (totalIncludingCheques <= 0) {
            throw new BusinessRuleException(Constants.ERR_PAYMENT_NEGATIVE);
        }
        return totalPago;
    }

    /**
     * ARCHITECTURAL NOTE: Atomic Updates
     * ----------------------------------
     * We calculate the tentative new balance and state here in Java ONLY for local variables
     * (e.g. if we need to return it). The actual source of truth is calculated dynamically
     * inside `repository.deductDeudaAtomic()`.
     *
     * If rowsAffected == 0, it means another thread modified this debt concurrently.
     * We MUST throw a BusinessRuleException so the frontend catches the HTTP 409
     * and refreshes the stale data.
     */
    private void updateDebtBalanceAtomically(Long id, double totalPago, DeudaResponse deuda) {
        int rowsAffected = repository.deductDeudaAtomic(id, totalPago, deuda.getMontoOriginal());
        if (rowsAffected == 0) {
            throw new BusinessRuleException("Error de concurrencia al actualizar la deuda o monto de pago excesivo.");
        }
    }

    private void processPaymentRecords(Long id, List<PagoDeudaRequest> pagos, DeudaResponse deuda, Long usuarioId) {
        com.centralizesys.model.sales.MetodoPago saldoMethod = metodoPagoRepository.findByAcronimo("SALDO")
                .orElseThrow(() -> new BusinessRuleException("Método de pago SALDO no configurado."));
        Long saldoId = saldoMethod.getId();

        for (PagoDeudaRequest pago : pagos) {
            if (pago.getMontoPago() > 0) {
                processSinglePaymentRecord(id, pago, deuda, saldoId, usuarioId);
            }
        }
    }

    private void processSinglePaymentRecord(Long id, PagoDeudaRequest pago, DeudaResponse deuda, Long saldoId, Long usuarioId) {
        if (pago.getFechaCobro() != null) {
            // It's a cheque payment for a debt!
            AlertaCheque cheque = new AlertaCheque();
            cheque.setVentaId(deuda.getVentaId());
            cheque.setMonto(pago.getMontoPago());
            cheque.setFechaCobro(pago.getFechaCobro());
            cheque.setEstado(DebtStatus.PENDIENTE.name());
            cheque.setTipoOrigen("DEUDA_FIADO");
            alertaChequeRepository.save(cheque);
        } else {
            // Standard cash/transfer
            com.centralizesys.model.sales.MetodoPago metodo = metodoPagoRepository.findById(pago.getMetodoPagoId())
                    .orElseThrow(() -> new BusinessRuleException("Método de pago no encontrado."));
            if (!metodo.isActivo()) {
                throw new BusinessRuleException("El método de pago '" + metodo.getDescripcion() + "' se encuentra desactivado.");
            }

            if (saldoId != null && saldoId.equals(pago.getMetodoPagoId())) {
                if (deuda.getClienteId() == null) {
                    throw new BusinessRuleException("El pago con Saldo a Favor requiere un cliente seleccionado.");
                }
                int rows = clienteRepository.deductSaldo(deuda.getClienteId(), pago.getMontoPago());
                if (rows == 0) {
                    throw new BusinessRuleException("Saldo a favor insuficiente para el cliente.");
                }
            }
            String obs = pago.getObservaciones();
            if (obs != null && obs.length() > 255) obs = obs.substring(0, 255);
            repository.insertarPagoDeuda(id, pago.getMetodoPagoId(), pago.getMontoPago(), obs, usuarioId);
        }
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
        int pagoRowsAffected = repository.updatePagoAnulado(pagoId);
        if (pagoRowsAffected == 0) {
            throw new BusinessRuleException("El pago está siendo anulado por otro proceso o ya ha sido anulado.");
        }

        int rowsAffected = repository.addDeudaAtomic(deuda.getId(), pago.getMonto(), deuda.getMontoOriginal());
        if (rowsAffected == 0) {
            throw new BusinessRuleException("Error de concurrencia al restaurar la deuda.");
        }

        // Restore Saldo a Favor (Vector 10)
        metodoPagoRepository.findById(pago.getMetodoPagoId())
                .filter(mp -> "SALDO".equals(mp.getAcronimo()))
                .ifPresent(mp -> {
                    if (deuda.getClienteId() != null) {
                        clienteRepository.addSaldo(deuda.getClienteId(), pago.getMonto());
                    }
                });

        // 6. Audit
        Long currentUserId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
        auditoriaService.registrarAccion(currentUserId, "PAGO_DEUDA",
                "Anulación de pago ID " + pagoId + " por $" + pago.getMonto() + ". Deuda ID: " + deuda.getId());
    }
}