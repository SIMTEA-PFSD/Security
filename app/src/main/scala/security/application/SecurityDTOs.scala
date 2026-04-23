package src.main.scala.security.application


// ─── Comando ──────────────────────────────────────────────────
/**
 * Datos deserializados del evento PasajeroRegistrado que llega
 * de Kafka (tópico "registro.pasajero", producido por Check-in).
 *
 * Corresponde exactamente al payload que Check-in serializa:
 * {
 *   "equipajeId": "...",
 *   "payload": {
 *     "pasajeroId": "...",
 *     "vueloId":    "...",
 *     "equipajeId": "..."
 *   }
 * }
 *
 * Y también acepta llamadas manuales del endpoint HTTP con:
 * {
 *   "equipajeId": "...",
 *   "pasajeroId": "...",
 *   "vueloId":    "...",
 *   "codigoRFID": "...",
 *   "peso":       23.0
 * }
 */
final case class InspeccionCommand(
                                    equipajeId: String,
                                    pasajeroId: String,
                                    vueloId:    String,
                                    codigoRFID: String,
                                    peso:       Double
                                  )

// ─── Errores ──────────────────────────────────────────────────
sealed trait SecurityError { def mensaje: String }

object SecurityError {
  final case class PesoExcedido(peso: Double)
    extends SecurityError {
    val mensaje = s"Equipaje excede el peso máximo permitido: ${peso}kg"
  }
  final case class CodigoRFIDInvalido(codigo: String)
    extends SecurityError {
    val mensaje = s"Código RFID inválido o no registrado: $codigo"
  }
  final case class EquipajeYaInspeccionado(equipajeId: String)
    extends SecurityError {
    val mensaje = s"El equipaje $equipajeId ya fue inspeccionado"
  }
  final case class ErrorPersistencia(msg: String)
    extends SecurityError {
    val mensaje = s"Error de persistencia: $msg"
  }
  final case class ErrorPublicacion(msg: String)
    extends SecurityError {
    val mensaje = s"Error al publicar evento: $msg"
  }
}

// ─── Respuesta ────────────────────────────────────────────────
final case class InspeccionResponse(
                                     inspeccionId: String,
                                     equipajeId:   String,
                                     resultado:    String,   // "APROBADO" o "RECHAZADO"
                                     motivo:       Option[String]
                                   )
