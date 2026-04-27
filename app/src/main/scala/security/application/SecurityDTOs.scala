package src.main.scala.security.application


final case class InspeccionCommand(
                                    equipajeId: String,
                                    pasajeroId: String,
                                    vueloId:    String,
                                    codigoRFID: String,
                                    peso:       Double
                                  )


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


final case class InspeccionResponse(
                                     inspeccionId: String,
                                     equipajeId:   String,
                                     resultado:    String,   //aprobao o f
                                     motivo:       Option[String]
                                   )
