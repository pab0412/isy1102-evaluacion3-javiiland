package com.example.javiiland.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.javiiland.model.Usuario;
import com.example.javiiland.model.dto.LoginResponseDto;
import com.example.javiiland.model.dto.RegistroUsuarioDto;
import com.example.javiiland.repository.UsuarioRepository;
import com.example.javiiland.security.JwtService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsuarioServicePrivacyTest {

    private static final String PASSWORD_EN_TEXTO_PLANO = "ClavePrivada123";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void cp05_guardaContrasenaHasheadaYNoLaIncluyeEnLaRespuesta() throws Exception {
        RegistroUsuarioDto request = new RegistroUsuarioDto(
                "usuario.qa", PASSWORD_EN_TEXTO_PLANO, "qa@example.com", "Usuario QA");
        when(usuarioRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(usuarioRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(9L);
            return usuario;
        });
        when(jwtService.generarToken(any(Usuario.class))).thenReturn("jwt-de-prueba");

        LoginResponseDto response = usuarioService.registrar(request);

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        String passwordPersistida = usuarioCaptor.getValue().getPassword();
        assertNotEquals(PASSWORD_EN_TEXTO_PLANO, passwordPersistida);
        assertTrue(passwordPersistida.startsWith("$2"));

        JsonNode respuestaJson = new ObjectMapper().valueToTree(response);
        assertFalse(respuestaJson.has("password"));
        assertFalse(respuestaJson.path("usuario").has("password"));
        assertFalse(respuestaJson.toString().contains(PASSWORD_EN_TEXTO_PLANO));
    }
}
