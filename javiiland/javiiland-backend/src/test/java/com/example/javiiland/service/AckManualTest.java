package com.example.javiiland.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rabbitmq.client.Channel;

@ExtendWith(MockitoExtension.class)
class AckManualTest {

    @Mock
    private Channel channel;

    @Mock
    private AckManual.Tarea tarea;

    @InjectMocks
    private AckManual ackManual;

    @Test
    void cp04_fallaTrasReintentosYDerivaAMensajeNoReconocido() throws Exception {
        long deliveryTag = 103L;
        doThrow(new IllegalStateException("Fallo de procesamiento")).when(tarea).run();

        assertDoesNotThrow(() -> ackManual.ejecutar(channel, deliveryTag, "q.reserva.creada", tarea));

        verify(tarea, times(3)).run();
        verify(channel, never()).basicAck(deliveryTag, false);
        verify(channel).basicNack(deliveryTag, false, false);
    }
}
