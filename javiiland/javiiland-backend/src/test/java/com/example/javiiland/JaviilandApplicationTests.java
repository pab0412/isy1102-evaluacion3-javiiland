package com.example.javiiland;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Deshabilitado: el contexto completo requiere servicios externos; las pruebas unitarias se ejecutan de forma aislada")
class JaviilandApplicationTests {

    @Test
    void contextLoads() {
    }

}