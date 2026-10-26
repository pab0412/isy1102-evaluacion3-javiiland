package com.example.javiiland.messaging;

import java.time.LocalDate;

import com.example.javiiland.model.TipoReserva;

public record ReservaCreadaEvent(Long reservaId, LocalDate fechaReserva, TipoReserva tipo) {}