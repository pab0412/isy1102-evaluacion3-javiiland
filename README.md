# Javiiland - Plataforma de Gestión de Reservas

Repositorio del proyecto semestral para la asignatura **ISY1102 - Seguridad y Calidad en el Desarrollo de Software** (Duoc UC). Este sistema integra una arquitectura orientada a microservicios contenerizados mediante Docker Compose, implementando suites de aseguramiento de calidad (QA), pruebas dinámicas unitarias, auditoría estática de código (SAST), análisis de rendimiento bajo concurrencia y validación de interfaces de usuario.

---

## Stack Tecnológico

* **Backend:** Java 17, Spring Boot 3, Spring Data JPA, Spring Security (JWT), Spring Boot Actuator.
* **Frontend:** React + Vite servido a través de servidor web NGINX (puerto `4173`).
* **Base de Datos:** MySQL 8 (esquema `javiiland`, expuesto en puerto `3307`).
* **Mensajería Asíncrona:** RabbitMQ con plugin `rabbitmq_delayed_message_exchange` y Dead Letter Exchange (DLX).
* **Entorno de Pruebas y QA:**
  * **Pruebas Unitarias:** JUnit 5, Mockito y base de datos volátil en memoria H2.
  * **Cobertura de Código:** JaCoCo (`jacoco-maven-plugin`).
  * **Análisis Estático (SAST):** SonarQube Community Edition (PostgreSQL).
  * **Pruebas de Carga y Rendimiento:** Apache JMeter 5.6.3.
  * **Auditoría de Calidad Web:** Google Lighthouse.

---

##  Requisitos Previos

* [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado y en ejecución en Windows/Linux/macOS con soporte para Docker Compose v2+.
* [Git](https://git-scm.com/) para clonar el repositorio.
* *(Opcional)* Java JDK 17+ y Maven instalados localmente si se desea ejecutar las suites de pruebas unitarias fuera de Docker mediante Maven Wrapper (`.\mvnw.cmd`).

---

##  Despliegue con Docker Compose

El proyecto cuenta con una orquestación centralizada en `docker-compose.yml` que gestiona el orden de arranque condicionado mediante chequeos de salud (`service_healthy`):

### 1. Clonar el repositorio
```powershell
git clone https://github.com/SebastianPinoB/DSY1107-002D-sebastian-pino.git
cd DSY1107-002D-sebastian-pino
```

### 2. Levantar la infraestructura
Para compilar y levantar todos los contenedores en segundo plano:
```powershell
docker compose up -d --build
```

### 3. Verificar el estado de los contenedores
```powershell
docker compose ps
```

---

## Servicios y Puertos Expuestos

| Servicio | Contenedor | URL / Puerto Local | Descripción |
| :--- | :--- | :--- | :--- |
| **Frontend Web** | `javiiland-frontend` | `http://localhost:4173` | Interfaz React optimizada bajo NGINX |
| **Backend API** | `javiiland-backend` | `http://localhost:8080` | API REST Spring Boot |
| **Health Check** | `javiiland-backend` | `http://localhost:8080/actuator/health` | Endpoint de monitoreo para pruebas JMeter |
| **Base de Datos** | `mysql-reservas` | `localhost:3307` | Motor MySQL 8 (Base de datos: `javiiland`) |
| **RabbitMQ Console** | `rabbitmq` | `http://localhost:15672` | Consola de administración (`guest` / `guest`) |
| **SonarQube** | `sonarqube` | `http://localhost:9000` | Panel de inspección estática de código |

---

## Ejecución de Pruebas y Aseguramiento de Calidad

### 1. Pruebas Unitarias y Cobertura (JUnit 5 + JaCoCo)
Las pruebas unitarias de negocio (`ReservaServiceTest`, `AckManualTest`, `UsuarioServicePrivacyTest`) se ejecutan de manera aislada utilizando Mockito y perfiles de prueba en memoria H2, sin requerir infraestructura externa activa:

```powershell
cd javiiland-backend
.\mvnw.cmd clean test jacoco:report
```

* **Resultado Obtenido:** 100% de pruebas aprobadas (`Failures: 0, Errors: 0, BUILD SUCCESS`).
* **Reporte HTML JaCoCo:** Se genera automáticamente en `javiiland-backend/target/site/jacoco/index.html`.

### 2. Análisis Estático de Código (SonarQube)
Con el contenedor de SonarQube levantado y operativo en el puerto `9000`:

```powershell
.\mvnw.cmd sonar:sonar `
  "-Dsonar.projectKey=javiiland-backend" `
  "-Dsonar.projectName=Javiiland Backend" `
  "-Dsonar.host.url=http://localhost:9000" `
  "-Dsonar.token=<TU_TOKEN_SONARQUBE>" `
  "-Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml"
```

* **Métricas Evaluadas:** Quality Gate superado (`Passed`), evaluando métricas de seguridad, fiabilidad, mantenibilidad técnica y tasa de duplicación (`0.0%`).

### 3. Pruebas de Rendimiento y Estrés (Apache JMeter)
* **Objetivo:** Medir la capacidad de respuesta y estabilidad de la API ante ráfagas de concurrencia.
* **Configuración:** 20 hilos (usuarios concurrentes), rampa de 5 segundos, 2 iteraciones (40 muestras totales).
* **Target:** Endpoint `/actuator/health`.
* **Métricas Obtenidas:** 
  * Tasa de Error: **`0.00%`**.
  * Tiempo Promedio de Respuesta (*Average*): **`11 ms`**.
  * Throughput: **`8.4 transacciones/segundo`**.

### 4. Auditoría de Cliente Web (Google Lighthouse)
Ejecución de auditoría sobre el frontend servido en producción por NGINX (`http://localhost:4173`):
* **Rendimiento:** 99/100 (FCP: 0,8 s, LCP: 0,8 s, TBT: 0 ms).
* **Accesibilidad:** 95/100.
* **Prácticas Recomendadas:** 100/100.
* **SEO:** 82/100.

---

## Normativas y Cumplimiento Legal

El diseño del software y su marco de pruebas integran los siguientes requisitos regulatorios:
1. **Ley N° 19.628 (Protección de la Vida Privada):** Validaciones unitarias sobre la persistencia, encapsulamiento y confidencialidad de datos sensibles de usuarios y clientes.
2. **Ley N° 21.459 (Delitos Informáticos):** Restricción de accesos no autorizados mediante tokens de autenticación JWT y mitigación de brechas potenciales identificadas mediante el análisis estático SAST.

---

## Reconocimientos y Proyecto Base

Este trabajo corresponde a una adaptación con fines estrictamente académicos para la evaluación técnica de calidad y seguridad de software:

* **Repositorio Base Original:** [DSY1107-002D-sebastian-pino](https://github.com/SebastianPinoB/DSY1107-002D-sebastian-pino)
* **Autor Original:** [SebastianPinoB](https://github.com/SebastianPinoB)
* **Alcance de las adaptaciones e instrumentación de QA añadidas:**
  * Implementación y aislamiento de la suite de pruebas unitarias con Mockito y base volátil H2 sin dependencias externas.
  * Habilitación e instrumentación de endpoints de monitoreo en Spring Boot Actuator para pruebas de carga concurrentes con Apache JMeter.
  * Centralización y orquestación multicontenedor mediante Docker Compose con control estricto de arranque condicionado (`service_healthy`).
  * Integración de pipelines de análisis estático con SonarQube y exportación de reportes de cobertura con JaCoCo.
