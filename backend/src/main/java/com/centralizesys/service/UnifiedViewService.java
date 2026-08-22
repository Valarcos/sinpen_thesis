package com.centralizesys.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UnifiedViewService {

    private final JdbcTemplate jdbcTemplate;

    public UnifiedViewService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getCobrosYPedidos() {
        String sql = """
            SELECT
                'FIADO' as tipo,
                d.id as id_referencia,
                d.venta_id,
                d.cliente_nombre,
                v.fecha as fecha_creacion,
                v.total_venta as monto_total,
                (SELECT COALESCE(SUM(costo_snapshot * cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as costo_total,
                (v.total_venta - d.monto_deuda) as monto_pagado,
                d.monto_deuda as saldo_restante,
                d.estado,
                v.tipo_venta,
                (SELECT COALESCE(SUM(cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as cantidad_productos,
                NULL as fecha_cobro,
                FALSE as has_cheque
            FROM deudores d
            JOIN ventas v ON d.venta_id = v.id
            WHERE d.estado IN ('PENDIENTE', 'PARCIAL')
            
            UNION ALL
            
            SELECT
                -- NOTE: CHEQUE is used as the discriminator so the frontend can correctly route
                -- the "Cobrar" action to POST /alertas/cheques/{id}/cobrar instead of /deudores/.
                'CHEQUE' as tipo,
                v.id as id_referencia,
                v.id as venta_id,
                v.cliente_nombre,
                v.fecha as fecha_creacion,
                v.total_venta as monto_total,
                (SELECT COALESCE(SUM(costo_snapshot * cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as costo_total,
                (SELECT COALESCE(SUM(monto), 0) FROM alertas_cheques WHERE venta_id = v.id AND estado = 'COBRADO') as monto_pagado,
                (SELECT COALESCE(SUM(monto), 0) FROM alertas_cheques WHERE venta_id = v.id AND estado = 'PENDIENTE') as saldo_restante,
                'PENDIENTE' as estado,
                v.tipo_venta,
                (SELECT COALESCE(SUM(cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as cantidad_productos,
                (SELECT MIN(fecha_cobro) FROM alertas_cheques WHERE venta_id = v.id AND estado = 'PENDIENTE') as fecha_cobro,
                FALSE as has_cheque
            FROM alertas_cheques ac
            JOIN ventas v ON ac.venta_id = v.id
            WHERE ac.estado = 'PENDIENTE' AND v.estado != 'PENDIENTE'
            GROUP BY v.id, v.cliente_nombre, v.fecha, v.total_venta, v.tipo_venta
        
            UNION ALL
        
            SELECT
                'PEDIDO' as tipo,
                p.id as id_referencia,
                NULL as venta_id,
                p.cliente_nombre,
                p.fecha as fecha_creacion,
                p.total_venta as monto_total,
                (SELECT COALESCE(SUM(costo_snapshot * cantidad), 0) FROM detalles_venta WHERE venta_id = p.id AND (anulado = false OR anulado IS NULL)) as costo_total,
                COALESCE((SELECT SUM(monto) FROM pagos_venta WHERE venta_id = p.id AND (anulado = false OR anulado IS NULL)), 0)
                    + COALESCE((SELECT SUM(monto) FROM alertas_cheques WHERE venta_id = p.id AND estado = 'PENDIENTE'), 0) as monto_pagado,
                p.total_venta
                    - COALESCE((SELECT SUM(monto) FROM pagos_venta WHERE venta_id = p.id AND (anulado = false OR anulado IS NULL)), 0)
                    - COALESCE((SELECT SUM(monto) FROM alertas_cheques WHERE venta_id = p.id AND estado = 'PENDIENTE'), 0) as saldo_restante,
                p.estado,
                p.tipo_venta,
                (SELECT COALESCE(SUM(cantidad), 0) FROM detalles_venta WHERE venta_id = p.id AND (anulado = false OR anulado IS NULL)) as cantidad_productos,
                -- [Epic 2 / Architectural Resolution A] Actively compute fecha_cobro from alertas_cheques.
                -- This replaces the hardcoded NULL that prevented the dashboard banner from triggering on Pedidos with cheques.
                (SELECT MIN(fecha_cobro) FROM alertas_cheques WHERE venta_id = p.id AND estado = 'PENDIENTE') as fecha_cobro,
                -- [Epic 2 / Architectural Resolution D] has_cheque flag drives the frontend "Masquerade" logic.
                -- When true: row appears as a teal Cheque badge in the Cheques tab, hidden from the Seña tab.
                CASE WHEN EXISTS (SELECT 1 FROM alertas_cheques WHERE venta_id = p.id AND estado = 'PENDIENTE') THEN TRUE ELSE FALSE END as has_cheque
            FROM ventas p
            WHERE p.estado = 'PENDIENTE'
        
            ORDER BY fecha_creacion DESC
            LIMIT 300
        """;

        return jdbcTemplate.queryForList(sql);
    }
}
