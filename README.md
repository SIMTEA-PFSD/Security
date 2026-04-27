# Security Service

Microservicio de inspección de equipaje del sistema de gestión de vuelos. Forma parte de un ecosistema de microservicios junto con **Check-in** y **Dispatcher**.

---

## Rol en el sistema

```
Check-in  ──→  [registro.pasajero]  ──→  Security  ──→  [equipaje.bodega]       ──→  Dispatcher
                                                    └──→  [vuelo.eventualidades]
```

| Acción   | Tópico Kafka              | Dirección |
|----------|---------------------------|-----------|
| Consume  | `registro.pasajero`       | ← Check-in publica aquí |
| Produce  | `equipaje.bodega`         | → Dispatcher consume aquí (aprobados) |
| Produce  | `vuelo.eventualidades`    | → Anomalías / rechazos |

---

## Qué hace

Por cada evento `PasajeroRegistrado` que llega de Kafka, el servicio:

1. Verifica que el equipaje no haya sido inspeccionado ya (idempotencia)
2. Aplica reglas de inspección: RFID válido, peso ≤ 23 kg, peso > 0
3. Persiste el resultado en PostgreSQL (auditoría)
4. Publica el evento correspondiente al tópico correcto

> **MVP**: Check-in no incluye `codigoRFID` ni `peso` en el payload. El consumer los simula automáticamente (`RFID-AUTO-{equipajeId}`, peso `10.0 kg`). En producción el escáner RFID los provee vía `POST /api/v1/inspeccionar`.

---

## Stack técnico

| Capa | Tecnología |
|---|---|
| Framework HTTP | Play Framework 3.0 (Scala 2.13) |
| Mensajería | Apache Kafka (kafka-clients 3.6) |
| Serialización | Circe (JSON) |
| Persistencia | PostgreSQL 16 + Doobie + HikariCP |
| Inyección de dependencias | Guice |
| Build | sbt 1.12 |

---

## Requisitos previos

- **Java 17+** (el proyecto usa `--release 17`)
- **sbt** instalado
- **Docker** con el stack de Check-in corriendo (Kafka en `localhost:9092`)
- **Docker** para la base de datos de Security

> El servicio NO levanta su propio Kafka. Se conecta al Kafka del microservicio Check-in.

---

## Levantar en modo desarrollo

### 1. Verificar que el Kafka de Check-in esté corriendo

```bash
docker ps | grep kafka
```

Debe aparecer el contenedor de Kafka del Check-in escuchando en el puerto `9092`.

### 2. Levantar solo la base de datos de Security

```bash
docker compose up -d postgres
```

Esto levanta un PostgreSQL en el puerto **5434** (diferente al de Check-in en 5432 y Dispatcher en 5433) con la base de datos `security_db` y ejecuta el `init.sql` automáticamente.

### 3. Correr el servicio

```bash
sbt run
```

Play arranca en modo dev en el puerto **9000**. El consumer Kafka y los módulos se inicializan al arranque gracias a la configuración de eager loading.

En los logs deberías ver:

```
[KafkaStarter] Consumer Kafka iniciado
[Consumer] Suscrito a tópico: registro.pasajero
Application started (Dev)
```

---

## Levantar en modo producción (Docker completo)

### 1. Publicar la imagen localmente

```bash
sbt Docker/publishLocal
```

### 2. Levantar todo el stack

```bash
docker compose --profile app up -d
```

Esto levanta tanto el PostgreSQL como el contenedor `security-service` en el puerto **8082**.

> En Docker, el servicio se conecta a Kafka usando `checkin-kafka:29092`. Ambos compose deben estar en la misma red Docker. Ver nota de integración en `docker-compose.yml`.

---

## Configuración (`conf/application.conf`)

| Clave | Valor por defecto | Variable de entorno |
|---|---|---|
| `security.http.port` | `8082` | `HTTP_PORT` |
| `security.kafka.bootstrap-servers` | `localhost:9092` | `KAFKA_BOOTSTRAP` |
| `security.kafka.topic-in` | `registro.pasajero` | `KAFKA_TOPIC_IN` |
| `security.kafka.topic-out` | `equipaje.bodega` | `KAFKA_TOPIC_OUT` |
| `security.kafka.topic-eventualidades` | `vuelo.eventualidades` | `KAFKA_TOPIC_EVENTUALIDADES` |
| `security.kafka.group-id` | `security-group-v2` | `KAFKA_GROUP_ID` |
| `security.db.url` | `jdbc:postgresql://localhost:5434/security_db` | `DB_URL` |
| `security.inspeccion.peso-maximo-kg` | `23.0` | — |

> **Nota sobre `group-id`**: Si el consumer ya corrió antes y se quieren reprocesar mensajes viejos del tópico, cambiar el `group-id` a un valor nuevo fuerza a Kafka a reiniciar los offsets desde `earliest`.

---

## Endpoints HTTP

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/health` | Estado del servicio |
| `POST` | `/api/v1/inspeccionar` | Inspección manual vía HTTP (escáner RFID) |
| `GET` | `/api/v1/inspecciones` | Listar todas las inspecciones |
| `GET` | `/api/v1/inspecciones/vuelo/:vueloId` | Inspecciones por vuelo con resumen |
| `GET` | `/api/v1/inspecciones/equipaje/:equipajeId` | Buscar inspección por equipaje |

### Ejemplo: inspección manual

```bash
curl -X POST http://localhost:9000/api/v1/inspeccionar \
  -H "Content-Type: application/json" \
  -d '{
    "equipajeId": "equip-001",
    "pasajeroId": "pas-123",
    "vueloId":    "AV-456",
    "codigoRFID": "RFID-001",
    "peso":       18.5
  }'
```

Respuesta (aprobado):
```json
{
  "inspeccionId": "uuid",
  "equipajeId":   "equip-001",
  "resultado":    "APROBADO",
  "motivo":       null
}
```

---

## Reglas de inspección

| Regla | Condición | Resultado |
|---|---|---|
| RFID válido | `codigoRFID` no puede estar vacío | RECHAZADO si falla |
| Peso máximo | `peso` ≤ 23.0 kg | RECHAZADO si excede |
| Peso positivo | `peso` > 0 | RECHAZADO si falla |
| Idempotencia | Mismo `equipajeId` no se inspecciona dos veces | Error `EquipajeYaInspeccionado` |

---

## Base de datos

Tabla principal: `inspecciones`

```sql
CREATE TABLE inspecciones (
    id           VARCHAR(64)   PRIMARY KEY,
    equipaje_id  VARCHAR(64)   NOT NULL UNIQUE,
    pasajero_id  VARCHAR(64)   NOT NULL,
    vuelo_id     VARCHAR(32)   NOT NULL,
    codigo_rfid  VARCHAR(64)   NOT NULL,
    peso         NUMERIC(5,2)  NOT NULL CHECK (peso > 0),
    resultado    VARCHAR(20)   NOT NULL CHECK (resultado IN ('APROBADO','RECHAZADO','PENDIENTE')),
    motivo       TEXT,
    timestamp    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
```

El script `init.sql` se ejecuta automáticamente en el primer arranque del contenedor de PostgreSQL.

---

## Problemas conocidos y soluciones

### El consumer no arranca sin una petición HTTP previa
Play en modo dev hace lazy loading por diseño — no inicializa ningún módulo hasta recibir la primera petición HTTP, lo que significa que el `KafkaStarter` y el consumer no arrancan solos al hacer `sbt run`.
```

En producción (Docker con `sbt stage`) este problema no existe — Play corre en modo producción y los eager singletons se inicializan al arranque sin excepción.

### Warning `LEADER_NOT_AVAILABLE` en Kafka
Normal en el primer arranque cuando el tópico no existía. Kafka lo crea automáticamente y el mensaje se envía igual. No requiere acción.

### Encoding roto en consola Windows (`t├│pico`)
PowerShell en Windows no renderiza UTF-8 por defecto. No afecta el funcionamiento. Para verlo bien:
```powershell
chcp 65001
```

### `curl` en PowerShell pide confirmación de seguridad
PowerShell alias de `curl` es `Invoke-WebRequest`. Usar en su lugar:
```powershell
Invoke-RestMethod http://localhost:9000/health
```

---

## Estructura del proyecto

```
app/
├── Module.scala                          # Wiring de Guice
├── KafkaStarter.scala                    # Arranque del consumer como eager singleton
├── controllers/
│   └── SecurityController.scala         # Endpoints Play
└── src/main/scala/security/
    ├── application/
    │   ├── InspeccionUseCase.scala       # Lógica de negocio
    │   └── SecurityDTOs.scala            # Command / Response / Errors
    ├── domain/
    │   ├── events/                       # EventoEquipaje, TipoEvento
    │   ├── model/                        # InspeccionEquipaje, ResultadoInspeccion
    │   └── ports/                        # InspeccionRepository, EventPublisher
    └── infraestructure/
        ├── config/AppConfig.scala
        ├── kafka/
        │   ├── KafkaEventConsumer.scala  # Adapter entrada
        │   └── KafkaEventPublisher.scala # Adapter salida
        └── persistence/
            └── PostgresInspeccionRepository.scala
conf/
├── application.conf
├── routes
└── logback.xml
docker-compose.yml
init.sql
build.sbt
```