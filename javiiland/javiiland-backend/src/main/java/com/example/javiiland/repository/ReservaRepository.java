package com.example.javiiland.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.javiiland.model.Reserva;
import com.example.javiiland.model.ReservaStatus;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
 
    boolean existsByFechaReservaAndEstatus(LocalDate date, ReservaStatus status);
 
    List<Reserva> findByFechaReservaBetweenAndEstatus(LocalDate start, LocalDate end, ReservaStatus status);
 
    List<Reserva> findByUsuarioId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("""
        update Reserva r 
           set r.asistenciaConfirmada = true, r.actualizadaEn = CURRENT_TIMESTAMP 
         where r.tokenConfirmacion = :token 
           and r.estatus = com.example.javiiland.model.ReservaStatus.CONFIRMED 
           and r.tipo = com.example.javiiland.model.TipoReserva.CLIENTE 
           and r.recordatorioEnviado = true 
           and r.asistenciaConfirmada = false
        """)
    int confirmarAsistencia(@Param("token") String token);

    @Modifying(clearAutomatically = true)
    @Query("""
        update Reserva r 
           set r.estatus = com.example.javiiland.model.ReservaStatus.CANCELLED, 
               r.actualizadaEn = CURRENT_TIMESTAMP 
         where r.id = :id 
           and r.estatus = com.example.javiiland.model.ReservaStatus.CONFIRMED 
           and r.tipo = com.example.javiiland.model.TipoReserva.CLIENTE 
           and r.recordatorioEnviado = true 
           and r.asistenciaConfirmada = false
        """)
    int expirar(@Param("id") Long id);

}
 