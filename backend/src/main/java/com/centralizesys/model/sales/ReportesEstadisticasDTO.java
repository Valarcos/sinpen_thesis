package com.centralizesys.model.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportesEstadisticasDTO {

    private RendimientoComercial rendimientoComercial;
    private FlujoDeCaja flujoDeCaja;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RendimientoComercial {
        private Double ingresosVentas;
        private Double costoTotalVendido;
        private Long productosVendidos;
        private Long productosComprados;
        private Double deudasPendientes;
        private Double deudasCtaCte;
        private Double chequesPorCobrar;
        private Double chequesExpirados;
        /** Total monetary value of orders in PENDIENTE state within the filtered period. */
        private Double ventasPendientes;
        /** Projected revenue: ingresosVentas + ventasPendientes. */
        private Double ventasTotalesProyectadas;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlujoDeCaja {
        private Double ingresosEfectivo;
        private Double egresosEfectivo; // Only inventory purchases
        private Double gastosVariosEfectivo; // Only gastos varios
        private Double balanceNeto;
    }
}
