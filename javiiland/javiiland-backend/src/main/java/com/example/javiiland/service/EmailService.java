package com.example.javiiland.service;

import com.example.javiiland.model.Reserva;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    @Value("${app.mail-from}") private String from;
    @Value("${app.simular-fallos:false}") private boolean simular;

    private void simularFallo() {
        if (simular) throw new RuntimeException("Fallo simulado del proveedor de correo");
    }

    private void enviar(String to, String asunto, String html, String adjuntoNombre, byte[] adjunto) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, adjunto != null, "UTF-8");
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(asunto);
            h.setText(html, true);
            if (adjunto != null) h.addAttachment(adjuntoNombre, new ByteArrayResource(adjunto));
            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new IllegalStateException("No se pudo enviar el correo", e);
        }
    }

    public void enviarConfirmacion(Reserva r, byte[] qrPng) {
        simularFallo();
        enviar(r.getUsuario().getEmail(), "Reserva confirmada: " + r.getNombreEvento(),
            "<p>Tu reserva para <b>" + r.getNombreEvento() + "</b> el <b>" + r.getFechaReserva()
            + "</b> está confirmada. Adjuntamos tu pase digital.</p>", "pase.png", qrPng);
    }

    public void enviarRecordatorio(Reserva r, String enlace) {
        simularFallo();
        enviar(r.getUsuario().getEmail(), "Confirma tu asistencia: " + r.getNombreEvento(),
            "<p>Tu evento es el <b>" + r.getFechaReserva() + "</b> (en 3 días).</p>"
            + "<p><a href=\"" + enlace + "\">Confirmar asistencia</a></p>"
            + "<p>Si no confirmas en 48 horas, la reserva se cancelará.</p>", null, null);
    }

    public void enviarCancelacionPorFaltaDeConfirmacion(Reserva r) {
        simularFallo();
        enviar(r.getUsuario().getEmail(), "Reserva cancelada: " + r.getNombreEvento(),
            "<p>Cancelamos tu reserva del <b>" + r.getFechaReserva()
            + "</b> por falta de confirmación. La fecha quedó disponible.</p>", null, null);
    }

    public void enviarAlertaAdmin(String cola, int mensajes) {
        log.error("ALERTA: {} tiene {} mensajes", cola, mensajes);
        // Aquí puedes enviar un correo al admin o un webhook a Slack
    }
}