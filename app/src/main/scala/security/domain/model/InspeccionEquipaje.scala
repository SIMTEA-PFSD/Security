package src.main.scala.security.domain.model

import java.time.Instant
import java.util.UUID

/**
 * «entity» — Registro de la inspección de seguridad de un equipaje.
 *
 * Se persiste en la base de datos de Security para auditoría.
 * Cada equipaje que pasa por el escáner genera exactamente una InspeccionEquipaje.
 */
final case class InspeccionEquipaje(
                                     id:          String,
                                     equipajeId:  String,
                                     pasajeroId:  String,
                                     vueloId:     String,
                                     codigoRFID:  String,
                                     peso:        Double,
                                     resultado:   EstadoInspeccion,
                                     motivo:      Option[String],   // solo si fue RECHAZADO
                                     timestamp:   Instant
                                   )

/**
 * Estados posibles de una inspección.
 * Son distintos de EstadoEquipaje — este es el estado del proceso de Security.
 */
sealed trait EstadoInspeccion { def nombre: String }

object EstadoInspeccion {
  case object Pendiente  extends EstadoInspeccion { val nombre = "PENDIENTE"  }
  case object Aprobado   extends EstadoInspeccion { val nombre = "APROBADO"   }
  case object Rechazado  extends EstadoInspeccion { val nombre = "RECHAZADO"  }
}

object InspeccionEquipaje {
  def crear(
             equipajeId: String,
             pasajeroId: String,
             vueloId:    String,
             codigoRFID: String,
             peso:       Double,
             resultado:  EstadoInspeccion,
             motivo:     Option[String] = None
           ): InspeccionEquipaje =
    InspeccionEquipaje(
      id         = UUID.randomUUID().toString,
      equipajeId = equipajeId,
      pasajeroId = pasajeroId,
      vueloId    = vueloId,
      codigoRFID = codigoRFID,
      peso       = peso,
      resultado  = resultado,
      motivo     = motivo,
      timestamp  = Instant.now()
    )
}
