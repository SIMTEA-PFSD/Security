-- ════════════════════════════════════════════════════════════════
--  Inicialización de la base de datos de Security
--  Se ejecuta automáticamente al primer arranque del contenedor
-- ════════════════════════════════════════════════════════════════

\connect security_db;

-- Registro de auditoría de cada inspección de equipaje
-- Una fila por equipajeId — restricción UNIQUE para garantizar
-- que el repositorio es idempotente (no se puede guardar dos veces
-- el mismo equipaje).
CREATE TABLE IF NOT EXISTS inspecciones (
    id           VARCHAR(64)    PRIMARY KEY,
    equipaje_id  VARCHAR(64)    NOT NULL UNIQUE,  -- un equipaje = una inspección
    pasajero_id  VARCHAR(64)    NOT NULL,
    vuelo_id     VARCHAR(32)    NOT NULL,
    codigo_rfid  VARCHAR(64)    NOT NULL,
    peso         NUMERIC(5, 2)  NOT NULL CHECK (peso > 0),
    resultado    VARCHAR(20)    NOT NULL CHECK (resultado IN ('APROBADO', 'RECHAZADO', 'PENDIENTE')),
    motivo       TEXT,          -- NULL si resultado = APROBADO
    timestamp    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Índices para las consultas del repositorio y del Spark Batch
CREATE INDEX IF NOT EXISTS idx_inspecciones_equipaje  ON inspecciones(equipaje_id);
CREATE INDEX IF NOT EXISTS idx_inspecciones_vuelo     ON inspecciones(vuelo_id);
CREATE INDEX IF NOT EXISTS idx_inspecciones_resultado ON inspecciones(resultado);
CREATE INDEX IF NOT EXISTS idx_inspecciones_timestamp ON inspecciones(timestamp DESC);
