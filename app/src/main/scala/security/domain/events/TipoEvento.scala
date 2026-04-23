package src.main.scala.security.domain.events

/**
 * Tipos de eventos del sistema completo.
 *
 * Security CONSUME:
 *   - PasajeroRegistrado ← "registro.pasajero"    (viene de Check-in)
 *
 * Security PRODUCE:
 *   - EquipajeEscaneado  → "equipaje.seguridad"   (log interno, opcional)
 *   - EquipajeAsignado   → "equipaje.bodega"       (equipaje APROBADO, lo consume Dispatcher)
 *   - EventualidadDetect → "vuelo.eventualidades"  (equipaje RECHAZADO)
 */
sealed trait TipoEvento { def topico: String }

object TipoEvento {
  // Producido por Check-in — Security lo CONSUME
  case object PasajeroRegistrado    extends TipoEvento { val topico = "registro.pasajero"    }

  // Producido por Security
  case object EquipajeEscaneado     extends TipoEvento { val topico = "equipaje.seguridad"   }
  case object EquipajeAsignado      extends TipoEvento { val topico = "equipaje.bodega"      }
  case object EventualidadDetectada extends TipoEvento { val topico = "vuelo.eventualidades" }

  // Producido por Dispatcher — Security no lo usa pero lo documenta
  case object VehiculoDespachado    extends TipoEvento { val topico = "equipaje.despacho"    }
}
