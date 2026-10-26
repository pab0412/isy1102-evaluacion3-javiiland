package com.example.javiiland.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.javiiland.config.SecurityConfig;
import com.example.javiiland.service.ReservaService;

@WebMvcTest(
        controllers = ReservaController.class,
        properties = {
                "javiiland.security.jwt-secret=01234567890123456789012345678901",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example"
        })
@Import({ SecurityConfig.class, ReservaControllerMvcTest.WebSecurityTestConfiguration.class })
class ReservaControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservaService reservaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void rejectInvalidJwtTokens() {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Token JWT inválido"));
    }

    @Test
    void cp02_rechazaReservaConFechaPasada() throws Exception {
        String request = """
                {
                  "reservationDate": "%s",
                  "eventName": "Evento inválido"
                }
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/reservas")
                        .with(jwt().jwt(token -> token.claim("userId", 7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reservaService);
    }

    @Test
    void cp03_rechazaSolicitudProtegidaSinTokenYConTokenManipulado() throws Exception {
        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reservas")
                        .header("Authorization",
                                "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhdHRhY2tlciJ9.firma-manipulada"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityTestConfiguration {
    }
}
