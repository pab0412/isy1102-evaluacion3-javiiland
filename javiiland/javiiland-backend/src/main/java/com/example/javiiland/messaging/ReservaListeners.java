package com.example.javiiland.messaging;

import java.io.IOException;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.example.javiiland.config.RabbitConfig;
import com.example.javiiland.service.AckManual;
import com.example.javiiland.service.ReservaProcesos;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReservaListeners {
    private final ReservaProcesos procesos;
    private final AckManual ack;

    @RabbitListener(queues = RabbitConfig.Q_CREADA)
    public void creada(ReservaEvento ev, Channel ch,
                       @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        ack.ejecutar(ch, tag, RabbitConfig.Q_CREADA, () -> procesos.enviarConfirmacion(ev.reservaId()));
    }

    @RabbitListener(queues = RabbitConfig.Q_RECORDATORIO)
    public void recordatorio(ReservaEvento ev, Channel ch,
                             @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        ack.ejecutar(ch, tag, RabbitConfig.Q_RECORDATORIO, () -> procesos.enviarRecordatorio(ev.reservaId()));
    }

    @RabbitListener(queues = RabbitConfig.Q_EXPIRADA)
    public void expirada(ReservaEvento ev, Channel ch,
                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        ack.ejecutar(ch, tag, RabbitConfig.Q_EXPIRADA, () -> procesos.expirar(ev.reservaId()));
    }
}