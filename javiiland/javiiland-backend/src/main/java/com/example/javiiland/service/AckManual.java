package com.example.javiiland.service;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class AckManual {

    @FunctionalInterface
    public interface Tarea { void run() throws Exception; }

    private final RetryTemplate retry = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(1000, 2.0, 10000)
            .build();

    public void ejecutar(Channel channel, long tag, String cola, Tarea tarea) throws IOException {
        try {
            retry.execute(ctx -> {
                log.info("[{}] intento {}", cola, ctx.getRetryCount() + 1);
                tarea.run();
                return null;
            });
            channel.basicAck(tag, false);
            log.info("[✓ ÉXITO] {}", cola);
        } catch (Exception e) {
            log.error("[⚠ ERROR] {} falló tras 3 intentos: {}", cola, e.getMessage());
            channel.basicNack(tag, false, false);          // requeue=false -> DLX -> DLQ
            log.error("[→ DLX] mensaje enviado a Dead Letter Exchange");
        }
    }
}