package com.example.javiiland.controller;

import com.example.javiiland.service.ReservaProcesos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConfirmacionController {

    private final ReservaProcesos service;

    @PostMapping("/api/reservas/confirmar-asistencia")
    public ResponseEntity<String> confirmar(@RequestParam String token) {
        return service.confirmarAsistencia(token)
            ? ResponseEntity.ok("¡Asistencia confirmada!")
            : ResponseEntity.badRequest().body("El enlace no es válido o ya expiró.");
    }
}