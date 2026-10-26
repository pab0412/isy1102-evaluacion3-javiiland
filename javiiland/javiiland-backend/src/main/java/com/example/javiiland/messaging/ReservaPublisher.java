package com.example.javiiland.messaging;

import com.example.javiiland.config.RabbitConfig;
import com.example.javiiland.model.TipoReserva;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
@Component
@RequiredArgsConstructor
public class ReservaPublisher {
    private static final int DIAS_ANTES = 3;
    private static final LocalTime HORA_RECORDATORIO = LocalTime.of(9, 0);
    private static final long MAX_DELAY_MS = 4_294_967_295L;   // límite del plugin (~49 días)

    private final RabbitTemplate rabbit;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservaCreada(ReservaCreadaEvent e) {
        if (e.tipo() != TipoReserva.CLIENTE) return;            // los bloqueos no notifican

        var msg = new ReservaEvento(e.reservaId());
        rabbit.convertAndSend(RabbitConfig.EX, "reserva.creada", msg);

        LocalDateTime cuando = e.fechaReserva().minusDays(DIAS_ANTES).atTime(HORA_RECORDATORIO);
        long delay = Duration.between(LocalDateTime.now(), cuando).toMillis();
        if (delay <= 0) return;                                  // muy próxima: sin reconfirmación

        rabbit.convertAndSend(RabbitConfig.EX_DELAYED, "reserva.recordatorio_confirmacion", msg, m -> {
            m.getMessageProperties().setDelayLong(Math.min(delay, MAX_DELAY_MS));
            return m;
        });
    }
}