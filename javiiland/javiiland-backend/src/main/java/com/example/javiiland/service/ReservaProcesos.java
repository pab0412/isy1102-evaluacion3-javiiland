package com.example.javiiland.service;

import com.example.javiiland.config.RabbitConfig;
import com.example.javiiland.model.Reserva;
import com.example.javiiland.messaging.ReservaEvento;
import com.example.javiiland.model.ReservaStatus;
import com.example.javiiland.model.TipoReserva;
import com.example.javiiland.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservaProcesos {
    private final ReservaRepository reservas;
    private final EmailService email;
    private final QrService qr;
    private final RabbitTemplate rabbit;
    @Value("${app.url}") private String appUrl;

    private boolean aplica(Reserva r) {
        return r != null
            && r.getTipo() == TipoReserva.CLIENTE
            && r.getEstatus() == ReservaStatus.CONFIRMED;
    }

    // Caso 1: correo de confirmación + QR
    @Transactional
    public void enviarConfirmacion(Long id) {
        Reserva r = reservas.findById(id).orElse(null);
        if (!aplica(r) || r.isEmailEnviado()) return;                    // idempotente
        email.enviarConfirmacion(r, qr.generar("reserva:" + r.getId()));
        r.setEmailEnviado(true);
    }

    // Caso 2: token + solicitud de reconfirmación
    @Transactional
    public void enviarRecordatorio(Long id) {
        Reserva r = reservas.findById(id).orElse(null);
        if (!aplica(r) || r.isRecordatorioEnviado() || r.isAsistenciaConfirmada()) return;

        if (r.getTokenConfirmacion() == null) {
            r.setTokenConfirmacion(UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", ""));
        }

        // Arranca el reloj de 48 h. Si el email falla y se reintenta, un duplicado es inofensivo
        rabbit.convertAndSend("", RabbitConfig.Q_ESPERA_48H, new ReservaEvento(r.getId()));

        email.enviarRecordatorio(r, appUrl + "/confirmar-asistencia?token=" + r.getTokenConfirmacion());
        r.setRecordatorioEnviado(true);
    }

    // Caso 3: cancelar por falta de respuesta (esto libera la fecha)
    @Transactional
    public void expirar(Long id) {
        if (reservas.expirar(id) == 0) return;       // ya reconfirmó, canceló o es un bloqueo: se ignora
        Reserva r = reservas.findById(id).orElseThrow();
        email.enviarCancelacionPorFaltaDeConfirmacion(r);
    }

    @Transactional
    public boolean confirmarAsistencia(String token) {
        int actualizados = reservas.confirmarAsistencia(token);
        return actualizados > 0;
    }
}