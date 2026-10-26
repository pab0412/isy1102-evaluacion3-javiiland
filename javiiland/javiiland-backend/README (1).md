# Javiiland API

API de reservas de eventos infantiles (Spring Boot + MySQL) con procesamiento asíncrono mediante **RabbitMQ**: correo de confirmación con QR, reconfirmación de asistencia a los 3 días y liberación automática de fechas por falta de respuesta.

## Contenido

1. [Qué hace el sistema](#1-qué-hace-el-sistema)
2. [Arquitectura](#2-arquitectura)
3. [Modelo de datos y estados](#3-modelo-de-datos-y-estados)
4. [Topología de RabbitMQ](#4-topología-de-rabbitmq)
5. [Reintentos y Dead Letter Queue](#5-reintentos-y-dead-letter-queue)
6. [Estructura del proyecto](#6-estructura-del-proyecto)
7. [Requisitos y configuración](#7-requisitos-y-configuración)
8. [Cómo levantar el entorno](#8-cómo-levantar-el-entorno)
9. [Endpoints relevantes](#9-endpoints-relevantes)
10. [Cómo probar que funciona](#10-cómo-probar-que-funciona)
11. [Solución de problemas](#11-solución-de-problemas)
12. [Lo que falta](#12-lo-que-falta)
13. [Limitaciones conocidas](#13-limitaciones-conocidas)

---

## 1. Qué hace el sistema

Para reservar hay que estar registrado (JWT). Una reserva ocupa una **fecha completa** (una reserva `CONFIRMED` por día). Cuando se crea una reserva de cliente ocurren tres procesos en segundo plano:

| # | Caso de uso | Cuándo | Qué hace |
|---|---|---|---|
| 1 | **Confirmación** | Al crear la reserva | Envía un correo con los detalles y un código QR (pase digital). |
| 2 | **Reconfirmación** | 3 días antes del evento, a las 09:00 | Genera un token único y envía un correo con un enlace para confirmar asistencia. |
| 3 | **Expiración** | 48 h después del recordatorio, si no hubo respuesta | Cancela la reserva (`CANCELLED`), lo que libera la fecha, y envía un correo de cortesía. |

Si el proveedor de correo falla, la reserva del usuario **no se ve afectada**: la tarea se reintenta y, si sigue fallando, queda en una Dead Letter Queue para revisión.

Las reservas de tipo `BLOQUEO` (el admin ocupa un día sin cliente) **no generan correos, recordatorios ni expiración**.

---

## 2. Arquitectura

```
Cliente ──POST /reservas──► API ──► MySQL (guarda la reserva)
                             │
                             └─ tras el COMMIT ──► RabbitMQ
                                                    │
        ┌───────────────────────────────────────────┤
        │ reserva.creada                            │ reserva.recordatorio_confirmacion
        │ (exchange "reservas")                     │ (exchange "reservas.delayed", con retraso)
        ▼                                           ▼
 q.reserva.creada                         q.reserva.recordatorio
        │                                           │
        ▼                                           ▼
 Correo + QR                              Token + correo de reconfirmación
                                                    │
                                                    ▼
                                      q.reserva.expirada.espera  (TTL 48 h)
                                                    │  al vencer el TTL
                                                    ▼
                                      reserva.expirada ──► q.reserva.expirada
                                                                  │
                                                                  ▼
                                                  Cancela la reserva + correo de cortesía

Cualquier cola principal ──(3 intentos fallidos, basicNack)──► reservas.dlx ──► reservas.dlq
```

Decisiones de diseño importantes:

- **Publicación tras el commit.** `ReservaService` publica un evento de Spring dentro de la transacción y `ReservaPublisher` lo envía a RabbitMQ con `@TransactionalEventListener(AFTER_COMMIT)`. Nunca se publican reservas que se revirtieron.
- **No se puede anular un mensaje ya encolado.** Cuando el usuario reconfirma, el mensaje de expiración sigue en la cola. Al llegar su hora, `expirar` hace un `UPDATE` condicional que no afecta a nada si el usuario ya reconfirmó. El resultado para el usuario es el mismo.
- **Dos técnicas de retraso:**
  - Recordatorio (retraso distinto por reserva): plugin `rabbitmq_delayed_message_exchange`.
  - Expiración (retraso fijo de 48 h): cola con `x-message-ttl` + Dead Letter Exchange. No necesita plugin.
- **Idempotencia.** RabbitMQ garantiza *al menos una entrega*, así que un mensaje puede llegar dos veces. Cada proceso comprueba el estado y las banderas antes de actuar.
- **Condiciones de carrera.** Confirmar y expirar son `UPDATE ... WHERE` atómicos en MySQL; solo uno de los dos puede ganar.

---

## 3. Modelo de datos y estados

El enum `ReservaStatus` tiene solo dos valores (`CONFIRMED`, `CANCELLED`). Los estados intermedios se deducen de columnas booleanas de la tabla `reservas`:

| Situación | `estatus` | `recordatorio_enviado` | `asistencia_confirmada` |
|---|---|---|---|
| Reserva recién hecha | `CONFIRMED` | false | false |
| Pendiente de reconfirmar (correo de 3 días enviado) | `CONFIRMED` | true | false |
| Usuario reconfirmó asistencia | `CONFIRMED` | true | true |
| Cancelada por el usuario, el admin o expirada | `CANCELLED` | cualquiera | cualquiera |

Columnas agregadas a `reservas`:

| Columna | Uso |
|---|---|
| `email_enviado` | Evita reenviar el correo de confirmación si el mensaje se entrega dos veces |
| `recordatorio_enviado` | Indica que se pidió reconfirmar; sin esto la reserva nunca expira |
| `asistencia_confirmada` | El usuario hizo clic en el enlace |
| `token_confirmacion` | Token único (64 caracteres) del enlace del correo |

Si no usas `ddl-auto: update`, aplica:

```sql
ALTER TABLE reservas
  ADD COLUMN email_enviado TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN recordatorio_enviado TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN asistencia_confirmada TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN token_confirmacion VARCHAR(100) NULL,
  ADD UNIQUE KEY uk_token_confirmacion (token_confirmacion);
```

---

## 4. Topología de RabbitMQ

**Exchanges**

| Nombre | Tipo | Uso |
|---|---|---|
| `reservas` | topic | `reserva.creada` y `reserva.expirada` |
| `reservas.delayed` | `x-delayed-message` (topic) | `reserva.recordatorio_confirmacion`, con retraso |
| `reservas.dlx` | fanout | Recibe los mensajes rechazados de las colas principales |

**Colas**

| Cola | Consumidor | Notas |
|---|---|---|
| `q.reserva.creada` | `ReservaListeners.creada` | Correo + QR |
| `q.reserva.recordatorio` | `ReservaListeners.recordatorio` | Token + correo de reconfirmación |
| `q.reserva.expirada.espera` | (nadie) | TTL 48 h; al vencer pasa a `reserva.expirada` |
| `q.reserva.expirada` | `ReservaListeners.expirada` | Cancela y avisa |
| `reservas.dlq` | **nadie, a propósito** | Retención 7 días. Consumirla borraría los mensajes y se perdería la posibilidad de analizarlos o reprocesarlos |

Las colas principales **no tienen TTL ni `x-max-length`**: cualquiera de los dos mandaría a la DLQ mensajes que no han fallado.

El header `x-death` que RabbitMQ agrega automáticamente indica de qué cola vino cada mensaje de la DLQ, y es lo que usa el reproceso.

---

## 5. Reintentos y Dead Letter Queue

- Modo de ack **manual** y `prefetch: 1`.
- `AckManual` ejecuta cada tarea con un `RetryTemplate`: **3 intentos** con espera de **1 s y 2 s** entre ellos (con 3 intentos solo hay dos esperas).
- Si algún intento funciona → `basicAck`.
- Si los 3 fallan → `basicNack(requeue=false)` → el mensaje va a `reservas.dlx` → `reservas.dlq`.
- `DlqMonitor` revisa la DLQ cada minuto y alerta cuando crece, sin consumirla.
- `POST /api/admin/dlq/reprocesar` devuelve cada mensaje a su cola de origen. Úsalo solo **después** de corregir la causa del fallo.

---

## 6. Estructura del proyecto

```
com.example.javiiland
├── JaviilandApplication.java          (@EnableScheduling)
├── config
│   ├── RabbitConfig.java              exchanges, colas, DLX/DLQ, convertidor JSON
│   ├── SecurityConfig.java            (@EnableMethodSecurity; confirmar-asistencia público)
│   ├── AdminSeeder.java
│   └── GlobalExceptionHandler.java
├── controller
│   ├── ReservaController.java
│   ├── UsuarioController.java
│   ├── ConfirmacionController.java    POST /api/reservas/confirmar-asistencia
│   └── DlqAdminController.java        estado y reproceso de la DLQ
├── messaging
│   ├── ReservaCreadaEvent.java        evento interno de Spring (reservaId, fechaReserva, tipo)
│   ├── ReservaEvento.java             mensaje que viaja por RabbitMQ (reservaId)
│   ├── ReservaPublisher.java          publica tras el commit y programa el recordatorio
│   ├── ReservaListeners.java          los 3 consumidores
│   └── DlqMonitor.java                alertas
├── model
│   ├── Reserva.java, ReservaStatus.java, TipoReserva.java, Role.java, Usuario.java
│   └── dto/...
├── repository
│   ├── ReservaRepository.java         confirmarAsistencia() y expirar()
│   └── UsuarioRepository.java
└── service
    ├── ReservaService.java            crear, crearComoAdmin, confirmarAsistencia, cancelar...
    ├── ReservaProcesos.java           lógica de los 3 casos de uso (transaccional)
    ├── AckManual.java                 reintentos + ack/nack
    ├── EmailService.java              correos (JavaMailSender)
    ├── QrService.java                 genera el QR con ZXing
    └── UsuarioService.java
```

En la raíz del proyecto: `docker-compose.yml` y `Dockerfile.rabbit`.

---

## 7. Requisitos y configuración

**Requisitos:** Java 17+, Maven, Docker y Docker Compose.

**Dependencias del `pom.xml`** (además de Web, JPA, Security, Lombok y el conector MySQL):

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-amqp</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-mail</artifactId></dependency>
<dependency><groupId>org.springframework.retry</groupId><artifactId>spring-retry</artifactId></dependency>
<dependency><groupId>com.google.zxing</groupId><artifactId>javase</artifactId><version>3.5.3</version></dependency>
```

**Configuración.** Deja un solo archivo en `src/main/resources` (`application.yml` o `application.properties`; si hay ambos y repiten una clave, gana el `.properties`). Agrega, sin pisar tu datasource ni tu configuración de JWT:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 1
        default-requeue-rejected: false
        retry:
          enabled: false        # los reintentos los hace AckManual
  mail:
    host: localhost
    port: 1025                  # Mailpit en desarrollo

app:
  url: http://localhost:5173    # URL del frontend, usada en el enlace del correo
  mail-from: no-reply@javiiland.local
  simular-fallos: false         # true para probar la DLQ
```

**Seguridad:**
- `POST /api/reservas/confirmar-asistencia` debe ser `permitAll()`, porque el usuario puede abrir el correo sin sesión. El token hace de credencial.
- `@EnableMethodSecurity` en `SecurityConfig` para que funcione el `@PreAuthorize("hasRole('ADMIN')")` de `DlqAdminController`.

---

## 8. Cómo levantar el entorno

`docker-compose.yml`:

```yaml
services:
  rabbitmq:
    build:
      context: .
      dockerfile: Dockerfile.rabbit
    container_name: rabbitmq-reservas
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  mysql:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: reservas
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  mailpit:
    image: axllent/mailpit
    ports:
      - "1025:1025"   # SMTP
      - "8025:8025"   # bandeja web

volumes:
  rabbitmq_data:
  mysql_data:
```

`Dockerfile.rabbit` (instala el plugin de mensajes retrasados; **la versión del plugin debe coincidir con la de RabbitMQ**):

```dockerfile
FROM rabbitmq:3.13-management
ADD --chmod=644 https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v3.13.0/rabbitmq_delayed_message_exchange-3.13.0.ez /opt/rabbitmq/plugins/
RUN rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange
```

Arranque:

```bash
docker compose up -d --build
docker exec rabbitmq-reservas rabbitmq-plugins list | grep delayed   # debe mostrar [E*] rabbitmq_delayed_message_exchange
mvn clean compile
mvn spring-boot:run
```

| Servicio | URL |
|---|---|
| RabbitMQ Management | http://localhost:15672 (guest / guest) |
| Mailpit (correos capturados) | http://localhost:8025 |
| API | http://localhost:8080 |

> `docker compose down -v` borra **también** los volúmenes (incluida la base de datos MySQL). Úsalo solo si no tienes datos que conservar.

---

## 9. Endpoints relevantes

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/reservas/confirmar-asistencia?token=...` | Público | Reconfirma la asistencia. Responde 200 o 400 si el token no es válido o ya expiró |
| GET | `/api/admin/dlq` | ADMIN | Cantidad de mensajes en cada cola y en la DLQ |
| POST | `/api/admin/dlq/reprocesar` | ADMIN | Devuelve los mensajes de la DLQ a su cola de origen |

Los endpoints de reservas existentes (`crear`, `crearComoAdmin`, `cancelar`, etc.) no cambian; ahora `crear` y `crearComoAdmin` publican el evento tras el commit.

El enlace del correo apunta a `{app.url}/confirmar-asistencia?token=...` (página del frontend, ver [Lo que falta](#12-lo-que-falta)), que debe hacer el POST. No se usa un GET directo porque los antivirus y previsualizadores de enlaces lo abrirían y reconfirmarían la asistencia sin intervención del usuario.

---

## 10. Cómo probar que funciona

### 10.0 Verificación inicial

1. La app arranca sin errores.
2. En http://localhost:15672:
   - *Exchanges*: `reservas`, `reservas.delayed`, `reservas.dlx`.
   - *Queues*: `q.reserva.creada`, `q.reserva.recordatorio`, `q.reserva.expirada`, `q.reserva.expirada.espera`, `reservas.dlq`.

### 10.1 Caso 1: confirmación + QR

1. Inicia sesión y crea una reserva de cliente (con fecha futura).
2. En http://localhost:8025 debe llegar el correo con el adjunto `pase.png`.
3. En MySQL: `SELECT email_enviado FROM reservas WHERE id = ...;` → `1`.
4. Crea una reserva `BLOQUEO` como admin: **no** debe llegar ningún correo.

### 10.2 Caso 2: recordatorio y token

Para no esperar 3 días, en `ReservaPublisher` cambia temporalmente el cálculo de `cuando`:

```java
LocalDateTime cuando = LocalDateTime.now().plusMinutes(1);   // SOLO PARA PRUEBAS
```

1. Crea una reserva y espera ~1 minuto.
2. Llega el correo con el enlace. En MySQL: `estatus = CONFIRMED`, `recordatorio_enviado = 1`, `asistencia_confirmada = 0`, `token_confirmacion` con valor.
3. En RabbitMQ aparece un mensaje en `q.reserva.expirada.espera`.

### 10.3 Caso 3a: el usuario reconfirma

1. Para acortar la espera, en `RabbitConfig` cambia el TTL de `qEspera48h` a `60_000` (1 minuto) y **borra la cola `q.reserva.expirada.espera`** desde el panel de RabbitMQ antes de reiniciar (si no, RabbitMQ rechaza el cambio de argumentos con `PRECONDITION_FAILED`).
2. Repite el caso 2. Antes de que pase el minuto, toma el token de MySQL y ejecuta:
   ```bash
   curl -X POST "http://localhost:8080/api/reservas/confirmar-asistencia?token=EL_TOKEN"
   ```
3. Respuesta `¡Asistencia confirmada!` y `asistencia_confirmada = 1`.
4. Pasado el minuto, el mensaje de expiración se procesa pero `expirar` no cambia nada: la reserva sigue `CONFIRMED`.
5. Repetir la llamada con el mismo token devuelve 400.

### 10.4 Caso 3b: el usuario ignora el correo

1. Con el TTL de 1 minuto, repite el caso 2 y **no** confirmes.
2. Al minuto: `estatus = CANCELLED`, la fecha vuelve a aparecer disponible en el calendario y llega el correo de cancelación.
3. Usar el enlace después devuelve 400.

### 10.4 bis Reserva con menos de 3 días de anticipación

Crea una reserva para dentro de 2 días: se envía el correo de confirmación pero **no** se programa recordatorio, así que nunca expira.

### 10.5 Reintentos y DLQ

1. Pon `app.simular-fallos: true` y reinicia.
2. Crea una reserva. En los logs aparecen `intento 1`, `intento 2`, `intento 3` y `[→ DLX]`.
3. El mensaje aparece en `reservas.dlq` (en *Get messages* se ve el header `x-death` con la cola de origen). `DlqMonitor` registra la alerta en el siguiente minuto. `GET /api/admin/dlq` muestra el conteo.
4. Pon `simular-fallos: false`, reinicia y llama como admin a `POST /api/admin/dlq/reprocesar`. El mensaje vuelve a su cola y esta vez se procesa; `GET /api/admin/dlq` muestra la DLQ en 0.

### 10.6 Al terminar las pruebas

Restaura el cálculo original de `cuando` en `ReservaPublisher` (3 días antes, 09:00) y el TTL de `qEspera48h` a `48 * 60 * 60 * 1000`.

---

## 11. Solución de problemas

| Síntoma | Causa probable |
|---|---|
| `unknown exchange type 'x-delayed-message'` al arrancar | El plugin no está instalado: el compose usa `image:` en vez de `build:`, o la versión del plugin no coincide con la de RabbitMQ |
| `PRECONDITION_FAILED` al cambiar argumentos de una cola | RabbitMQ no permite redeclarar una cola con otros argumentos. Borra la cola desde el panel (o `docker compose down -v` si no te importa perder MySQL) |
| El `DlqMonitor` nunca corre | Falta `@EnableScheduling` en `JaviilandApplication` |
| `DlqAdminController` responde 200 a cualquiera o no restringe | Falta `@EnableMethodSecurity` en `SecurityConfig` |
| El enlace del correo da 403 | `POST /api/reservas/confirmar-asistencia` no está en `permitAll()` |
| No compila por `getEmail()` | Ajusta el nombre al campo real de `Usuario` |
| `LazyInitializationException` al enviar un correo | El acceso a `reserva.getUsuario()` ocurre fuera de transacción. Los métodos de `ReservaProcesos` son `@Transactional`; mantén el envío ahí |
| Ya no compila el código que usa `new Reserva(...)` | `@AllArgsConstructor` ahora recibe los 4 campos nuevos. Usa `Reserva.builder()` |
| Los mensajes llegan a la DLQ al instante | `simular-fallos` está en `true` o el servidor SMTP no está accesible |

---

## 12. Lo que falta

Funcionalidad pendiente, de mayor a menor importancia:

1. **Página del frontend `/confirmar-asistencia`.** Debe leer `?token=` de la URL, hacer `POST /api/reservas/confirmar-asistencia?token=...` y mostrar el resultado. Sin ella, el enlace del correo no funciona para el usuario final.
2. **Reintentos largos para la DLQ.** Hoy los reintentos duran solo ~3 segundos. Si el proveedor de correo cae por horas, los mensajes terminan en la DLQ y alguien debe pulsar "Reprocesar". Para que se reintenten solos en segundo plano, como pide el caso 1, hay que agregar una cola de espera (por ejemplo, 5 minutos con TTL) que devuelva el mensaje a su cola principal, o un `@Scheduled` que reprocese la DLQ automáticamente con un límite de intentos.
3. **Proveedor real de correo** (Resend o SendGrid) en lugar de Mailpit: configurar credenciales por variables de entorno y, si es necesario, reemplazar `JavaMailSender` por el SDK o la API del proveedor.
4. **Segundo canal de alertas.** `enviarAlertaAdmin` hoy solo registra en el log. Si se envía por correo y el proveedor está caído, la propia alerta fallará: conviene añadir Slack, Teams o un webhook.
5. **Reprogramación al editar la fecha.** `actualizar` puede cambiar `fechaReserva`, pero el recordatorio ya programado sigue calculado para la fecha antigua. Se resuelve validando en `enviarRecordatorio` que la fecha aún corresponda, o reprogramando al actualizar.
6. **Reservas con más de ~49 días de anticipación.** El plugin de mensajes retrasados admite como máximo ~49 días (2³² ms), así que el recordatorio llegaría antes de tiempo. Solución: que el consumidor compruebe si ya es el momento y, si no, se reprograme.
7. **Panel React de la DLQ** (opcional, si lo exige la evaluación): consulta `GET /api/admin/dlq` cada 5 s, muestra las tarjetas de cada cola y un botón "Reprocesar".
8. **Patrón Outbox.** `AFTER_COMMIT` evita publicar reservas revertidas, pero si RabbitMQ está caído justo después del commit, el evento se pierde. Para garantía total: guardar el evento en una tabla `outbox` dentro de la misma transacción y publicarlo con un `@Scheduled`.
9. **Zona horaria.** `LocalDateTime.now()` usa la del servidor. Fijar `ZoneId.of("America/Santiago")` en `ReservaPublisher` para que "09:00" sea hora de Chile.
10. **Pruebas automatizadas** de `ReservaProcesos` y de las consultas `confirmarAsistencia` / `expirar`.

---

## 13. Limitaciones conocidas

- Con el enum de dos estados no se distingue una reserva cancelada por el usuario de una expirada. Si hace falta para reportes, agregar una columna `motivo_cancelacion` (`USUARIO`, `EXPIRADA`) sin tocar el enum.
- `actualizar` con `status = CONFIRMED` sobre una reserva cancelada la reactiva, pero conserva las banderas y no reprograma recordatorios.
- `DlqMonitor` vuelve a alertar tras reiniciar la app si la DLQ ya tenía mensajes, porque guarda el último conteo en memoria.
- Los mensajes de la DLQ se borran solos a los 7 días, sin aviso.
- Los mensajes del plugin de retraso no se replican en un clúster de RabbitMQ.
- Con `prefetch: 1` y un consumidor por cola, el procesamiento es secuencial. Para el volumen esperado es suficiente; se puede subir si crece.
