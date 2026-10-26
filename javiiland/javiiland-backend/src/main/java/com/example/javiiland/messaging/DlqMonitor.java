package com.example.javiiland.messaging;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.javiiland.config.RabbitConfig;
import com.example.javiiland.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqMonitor {
    private final AmqpAdmin admin;
    private final EmailService email;
    private int ultimo = 0;

    @Scheduled(fixedDelay = 60_000)
    public void revisar() {
        var info = admin.getQueueInfo(RabbitConfig.DLQ);
        int actual = info == null ? 0 : (int) info.getMessageCount(); // <- Agregamos (int) aquí
        if (actual > ultimo) {
            log.error("[DLQ] {} mensajes en cuarentena ({} nuevos). Revisar logs.", actual, actual - ultimo);
            email.enviarAlertaAdmin(RabbitConfig.DLQ, actual);
        }
        ultimo = actual;
    }
}