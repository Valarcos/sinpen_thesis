package com.centralizesys.model.cheque;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertaChequeRequest {
    private Double monto = 0.0;
    private LocalDate fechaCobro;

    public void setMonto(Double monto) {
        this.monto = (monto != null) ? monto : 0.0;
    }
}
