package com.example.javiiland.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.javiiland.config.RabbitConfig;
import com.example.javiiland.messaging.ReservaCreadaEvent;
import com.example.javiiland.messaging.ReservaEvento;
import com.example.javiiland.messaging.ReservaPublisher;
import com.example.javiiland.model.Reserva;
import com.example.javiiland.model.ReservaStatus;
import com.example.javiiland.model.TipoReserva;
import com.example.javiiland.model.Usuario;
import com.example.javiiland.model.dto.ReservaRequestDto;
import com.example.javiiland.model.dto.ReservaResponseDto;
import com.example.javiiland.repository.ReservaRepository;
import com.example.javiiland.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ReservaService reservaService;

    @InjectMocks
    private ReservaPublisher reservaPublisher;

    @Test
    void cp01_creaReservaYPublicaEventoDeConfirmacion() {
        Long usuarioId = 17L;
        LocalDate fecha = LocalDate.now().plusDays(20);
        Usuario usuario = Usuario.builder()
                .id(usuarioId)
                .username("cliente")
                .nombre("Cliente de prueba")
                .build();
        ReservaRequestDto request = new ReservaRequestDto(fecha, "Celebración", "Descripción");

        when(reservaRepository.existsByFechaReservaAndEstatus(fecha, ReservaStatus.CONFIRMED)).thenReturn(false);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> {
            Reserva guardada = invocation.getArgument(0);
            guardada.setId(45L);
            return guardada;
        });

        ReservaResponseDto response = reservaService.crear(usuarioId, request);

        assertEquals(45L, response.getId());
        assertEquals(fecha, response.getFechaReserva());
        assertEquals("Celebración", response.getNombreEvento());
        assertEquals(ReservaStatus.CONFIRMED, response.getStatus());
        assertEquals(usuarioId, response.getUsuarioId());

        ArgumentCaptor<ReservaCreadaEvent> eventCaptor = ArgumentCaptor.forClass(ReservaCreadaEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertNotNull(eventCaptor.getValue());
        assertEquals(45L, eventCaptor.getValue().reservaId());
        assertEquals(fecha, eventCaptor.getValue().fechaReserva());
        assertEquals(TipoReserva.CLIENTE, eventCaptor.getValue().tipo());

        reservaPublisher.onReservaCreada(eventCaptor.getValue());
        verify(rabbitTemplate).convertAndSend(RabbitConfig.EX, "reserva.creada", new ReservaEvento(45L));
    }
}
