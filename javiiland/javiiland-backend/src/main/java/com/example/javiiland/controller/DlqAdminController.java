package com.example.javiiland.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.javiiland.config.RabbitConfig;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DlqAdminController {
   private final AmqpAdmin admin;
   private final RabbitTemplate rabbit;

   private int contar(String cola) {
      var info = admin.getQueueInfo(cola);
      return info == null ? 0 : (int) info.getMessageCount();
   }

   @GetMapping
   public Map<String, Integer> estado() {
      Map<String, Integer> r = new LinkedHashMap<>();
      RabbitConfig.COLAS.forEach(q -> r.put(q, contar(q)));
      r.put(RabbitConfig.DLQ, contar(RabbitConfig.DLQ));
      return r;
   }

   @PostMapping("/reprocesar")
   public int reprocesar() {
      int n = contar(RabbitConfig.DLQ), movidos = 0;
      for (int i = 0; i < n; i++) {
         Message m = rabbit.receive(RabbitConfig.DLQ);
         if (m == null)
            break;
         String origen = colaOrigen(m);
         if (origen == null) {
            rabbit.send("", RabbitConfig.DLQ, m);
            continue;
         }
         rabbit.send("", origen, m);
         movidos++;
      }
      return movidos;
   }

   private String colaOrigen(Message m) {
      var deaths = m.getMessageProperties().getXDeathHeader();
      if (deaths == null || deaths.isEmpty())
         return null;
      String q = (String) deaths.get(0).get("queue");
      return RabbitConfig.COLAS.contains(q) ? q : null;
   }
}