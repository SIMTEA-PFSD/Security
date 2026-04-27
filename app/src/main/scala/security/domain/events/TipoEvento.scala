package src.main.scala.security.domain.events

sealed trait TipoEvento { def topico: String }

object TipoEvento {

  case object PasajeroRegistrado    extends TipoEvento { val topico = "registro.pasajero"    }

  case object EquipajeEscaneado     extends TipoEvento { val topico = "equipaje.seguridad"   }
  case object EquipajeAsignado      extends TipoEvento { val topico = "equipaje.bodega"      }
  case object EventualidadDetectada extends TipoEvento { val topico = "vuelo.eventualidades" }

  case object VehiculoDespachado    extends TipoEvento { val topico = "equipaje.despacho"    }
}
