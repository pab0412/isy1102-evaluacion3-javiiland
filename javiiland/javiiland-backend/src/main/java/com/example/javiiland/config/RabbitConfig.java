package com.example.javiiland.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class RabbitConfig {

    // ===== Exchanges y colas principales =====
    public static final String EX = "reservas";
    public static final String EX_DELAYED = "reservas.delayed";
    public static final String Q_CREADA = "q.reserva.creada";
    public static final String Q_RECORDATORIO = "q.reserva.recordatorio";
    public static final String Q_EXPIRADA = "q.reserva.expirada";
    public static final String Q_ESPERA_48H = "q.reserva.expirada.espera";
    public static final List<String> COLAS = List.of(Q_CREADA, Q_RECORDATORIO, Q_EXPIRADA);

    // ===== DLX / DLQ (como en la guía) =====
    public static final String DLX = "reservas.dlx";
    public static final String DLQ = "reservas.dlq";

    @Bean TopicExchange reservasExchange() { return new TopicExchange(EX, true, false); }

    @Bean CustomExchange delayedExchange() {
        return new CustomExchange(EX_DELAYED, "x-delayed-message", true, false,
                Map.of("x-delayed-type", "topic"));
    }

    // Colas principales: SIN TTL ni max-length; solo apuntan al DLX
    private Queue principal(String nombre) {
        return QueueBuilder.durable(nombre).deadLetterExchange(DLX).build();
    }
    @Bean Queue qCreada()       { return principal(Q_CREADA); }
    @Bean Queue qRecordatorio() { return principal(Q_RECORDATORIO); }
    @Bean Queue qExpirada()     { return principal(Q_EXPIRADA); }

    // Cola de espera de 48 h: al vencer el TTL pasa a "reserva.expirada"
    @Bean Queue qEspera48h() {
        return QueueBuilder.durable(Q_ESPERA_48H)
                .ttl(48 * 60 * 60 * 1000)
                .deadLetterExchange(EX)
                .deadLetterRoutingKey("reserva.expirada")
                .build();
    }

    // DLX fanout + DLQ con retención de 7 días
    @Bean FanoutExchange deadLetterExchange() { return new FanoutExchange(DLX, true, false); }

    @Bean Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).ttl(7 * 24 * 60 * 60 * 1000).build();
    }

    @Bean Declarables bindings() {
        return new Declarables(
            BindingBuilder.bind(qCreada()).to(reservasExchange()).with("reserva.creada"),
            BindingBuilder.bind(qExpirada()).to(reservasExchange()).with("reserva.expirada"),
            BindingBuilder.bind(qRecordatorio()).to(delayedExchange())
                    .with("reserva.recordatorio_confirmacion").noargs(),
            BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange())
        );
    }

    @Bean MessageConverter jsonConverter() { return new Jackson2JsonMessageConverter(); }
}