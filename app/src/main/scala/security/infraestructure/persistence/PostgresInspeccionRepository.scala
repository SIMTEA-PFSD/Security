package src.main.scala.security.infraestructure.persistence

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.hikari.HikariTransactor
import src.main.scala.security.domain.model.{EstadoInspeccion, InspeccionEquipaje}
import src.main.scala.security.domain.ports.InspeccionRepository

import java.time.Instant
import scala.util.control.NonFatal

/**
 * «adapter» PostgreSQL para el puerto InspeccionRepository.
 *
 * Mapeos especiales:
 *   - EstadoInspeccion (sealed trait) ↔ VARCHAR  → Meta custom
 *   - Option[String]  (motivo)        ↔ columna nullable
 *   - Instant         (timestamp)     ↔ TIMESTAMPTZ (vía doobie-postgres)
 *
 * Todas las operaciones son síncronas vía unsafeRunSync —
 * mismo patrón que PostgresPasajeroRepository del Check-in.
 */
final class PostgresInspeccionRepository(
  xa: HikariTransactor[IO]
)(implicit runtime: IORuntime) extends InspeccionRepository {

  // ─── Mapeo EstadoInspeccion ↔ VARCHAR ────────────────────
  private implicit val estadoMeta: Meta[EstadoInspeccion] =
    Meta[String].timap(parseEstado)(_.nombre)

  private def parseEstado(s: String): EstadoInspeccion = s.toUpperCase match {
    case "APROBADO"  => EstadoInspeccion.Aprobado
    case "RECHAZADO" => EstadoInspeccion.Rechazado
    case "PENDIENTE" => EstadoInspeccion.Pendiente
    case otro        => throw new IllegalStateException(s"EstadoInspeccion desconocido en DB: $otro")
  }

  // ─── Read: orden de columnas igual al SELECT de abajo ────
  private implicit val inspeccionRead: Read[InspeccionEquipaje] =
    Read[(String, String, String, String, String, Double, EstadoInspeccion, Option[String], Instant)]
      .map { case (id, equipajeId, pasajeroId, vueloId, codigoRFID, peso, resultado, motivo, timestamp) =>
        InspeccionEquipaje(id, equipajeId, pasajeroId, vueloId, codigoRFID, peso, resultado, motivo, timestamp)
      }

  // ─── guardar ─────────────────────────────────────────────
  override def guardar(i: InspeccionEquipaje): Either[String, InspeccionEquipaje] = {
    val ins =
      sql"""
        INSERT INTO inspecciones (
          id, equipaje_id, pasajero_id, vuelo_id,
          codigo_rfid, peso, resultado, motivo, timestamp
        ) VALUES (
          ${i.id}, ${i.equipajeId}, ${i.pasajeroId}, ${i.vueloId},
          ${i.codigoRFID}, ${i.peso}, ${i.resultado}, ${i.motivo}, ${i.timestamp}
        )
        ON CONFLICT (id) DO UPDATE
          SET resultado = EXCLUDED.resultado,
              motivo    = EXCLUDED.motivo
      """.update.run

    try {
      ins.transact(xa).unsafeRunSync()
      Right(i)
    } catch {
      case NonFatal(ex) => Left(s"Error guardando inspección: ${ex.getMessage}")
    }
  }

  // ─── buscarPorEquipaje ───────────────────────────────────
  override def buscarPorEquipaje(equipajeId: String): Option[InspeccionEquipaje] = {
    val q =
      sql"""
        SELECT id, equipaje_id, pasajero_id, vuelo_id,
               codigo_rfid, peso, resultado, motivo, timestamp
        FROM inspecciones
        WHERE equipaje_id = $equipajeId
        LIMIT 1
      """.query[InspeccionEquipaje].option

    try q.transact(xa).unsafeRunSync()
    catch { case NonFatal(_) => None }
  }

  // ─── listarTodas ─────────────────────────────────────────
  override def listarTodas(): List[InspeccionEquipaje] = {
    val q =
      sql"""
        SELECT id, equipaje_id, pasajero_id, vuelo_id,
               codigo_rfid, peso, resultado, motivo, timestamp
        FROM inspecciones
        ORDER BY timestamp DESC
      """.query[InspeccionEquipaje].to[List]

    try q.transact(xa).unsafeRunSync()
    catch { case NonFatal(_) => List.empty }
  }

  // ─── listarPorVuelo ──────────────────────────────────────
  override def listarPorVuelo(vueloId: String): List[InspeccionEquipaje] = {
    val q =
      sql"""
        SELECT id, equipaje_id, pasajero_id, vuelo_id,
               codigo_rfid, peso, resultado, motivo, timestamp
        FROM inspecciones
        WHERE vuelo_id = $vueloId
        ORDER BY timestamp DESC
      """.query[InspeccionEquipaje].to[List]

    try q.transact(xa).unsafeRunSync()
    catch { case NonFatal(_) => List.empty }
  }
}
