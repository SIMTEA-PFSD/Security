package src.main.scala.security.application

import src.main.scala.security.domain.events.EventoEquipaje
import src.main.scala.security.domain.model.{EstadoInspeccion, InspeccionEquipaje, ResultadoInspeccion}
import src.main.scala.security.domain.ports.{EventPublisher, InspeccionRepository}

class InspeccionUseCase(
                         inspeccionRepo: InspeccionRepository,
                         publisher:      EventPublisher,
                         pesoMaximoKg:   Double = 23.0
                       ) {

  def ejecutar(cmd: InspeccionCommand): Either[SecurityError, InspeccionResponse] =
    for {
      _           <- verificarNoInspeccionado(cmd.equipajeId)
      resultado   <- inspeccionarEquipaje(cmd)
      inspeccion  <- persistirInspeccion(cmd, resultado)
      _           <- publicarEvento(resultado)
    } yield InspeccionResponse(
      inspeccionId = inspeccion.id,
      equipajeId   = cmd.equipajeId,
      resultado    = resultado match {
        case _: ResultadoInspeccion.Aprobado  => "APROBADO"
        case _: ResultadoInspeccion.Rechazado => "RECHAZADO"
      },
      motivo = resultado match {
        case r: ResultadoInspeccion.Rechazado => Some(r.motivo)
        case _                                => None
      }
    )

  /**
   * Verifica que el equipaje no haya pasado por seguridad antes.
   * Evita dobles procesamientos si Kafka re-entrega el mensaje.
   */
  private def verificarNoInspeccionado(
                                        equipajeId: String
                                      ): Either[SecurityError, Unit] =
    inspeccionRepo.buscarPorEquipaje(equipajeId) match {
      case Some(_) => Left(SecurityError.EquipajeYaInspeccionado(equipajeId))
      case None    => Right(())
    }

  private def inspeccionarEquipaje(cmd: InspeccionCommand): Either[SecurityError, ResultadoInspeccion] = {

    def rechazar(motivo: String): ResultadoInspeccion =
      ResultadoInspeccion.Rechazado(
        equipajeId = cmd.equipajeId,
        pasajeroId = cmd.pasajeroId,
        vueloId    = cmd.vueloId,
        motivo     = motivo
      )

    val reglas: List[(Boolean, String)] = List(
      (cmd.codigoRFID.trim.isEmpty, "Código RFID vacío o inválido"),
      (cmd.peso > pesoMaximoKg,     s"Peso ${cmd.peso}kg excede el máximo de ${pesoMaximoKg}kg"),
      (cmd.peso <= 0,               s"Peso inválido: ${cmd.peso}kg")
    )

    reglas.find(_._1) match {
      case Some((_, motivo)) => Right(rechazar(motivo))
      case None              => Right(ResultadoInspeccion.Aprobado(
        equipajeId = cmd.equipajeId,
        pasajeroId = cmd.pasajeroId,
        vueloId    = cmd.vueloId
      ))
    }
  }

  private def persistirInspeccion(
                                   cmd:       InspeccionCommand,
                                   resultado: ResultadoInspeccion
                                 ): Either[SecurityError, InspeccionEquipaje] = {

    val (estadoInspeccion, motivo) = resultado match {
      case _: ResultadoInspeccion.Aprobado  => (EstadoInspeccion.Aprobado, None)
      case r: ResultadoInspeccion.Rechazado => (EstadoInspeccion.Rechazado, Some(r.motivo))
    }

    val inspeccion = InspeccionEquipaje.crear(
      equipajeId = cmd.equipajeId,
      pasajeroId = cmd.pasajeroId,
      vueloId    = cmd.vueloId,
      codigoRFID = cmd.codigoRFID,
      peso       = cmd.peso,
      resultado  = estadoInspeccion,
      motivo     = motivo
    )

    inspeccionRepo.guardar(inspeccion)
      .left.map(SecurityError.ErrorPersistencia)
  }

  private def publicarEvento(
                              resultado: ResultadoInspeccion
                            ): Either[SecurityError, Unit] = {

    val evento = resultado match {
      case r: ResultadoInspeccion.Aprobado =>
        EventoEquipaje.equipajeAprobado(r.equipajeId, r.pasajeroId, r.vueloId)

      case r: ResultadoInspeccion.Rechazado =>
        EventoEquipaje.equipajeRechazado(r.equipajeId, r.pasajeroId, r.vueloId, r.motivo)
    }

    publisher.publicar(evento)
      .left.map(SecurityError.ErrorPublicacion)
  }
}
