package src.main.scala.security.application

import src.main.scala.security.domain.events.EventoEquipaje
import src.main.scala.security.domain.model.{EstadoInspeccion, InspeccionEquipaje}
import src.main.scala.security.domain.model.security.domain.model.ResultadoInspeccion
import src.main.scala.security.domain.ports.{EventPublisher, InspeccionRepository}

/**
 * ─────────────────────────────────────────────────────────────
 *  CASO DE USO: INSPECCIONAR EQUIPAJE
 * ─────────────────────────────────────────────────────────────
 *
 *  Es el núcleo de Security. Orquesta el flujo completo al
 *  recibir un equipaje para inspección (desde Kafka o HTTP):
 *
 *   1. Valida que el equipaje no haya sido inspeccionado ya
 *   2. Aplica las reglas de inspección (peso, RFID, etc.)
 *   3. Persiste el registro de auditoría
 *   4. Publica el evento correspondiente:
 *        → APROBADO : "equipaje.bodega"        (lo consume Dispatcher)
 *        → RECHAZADO: "vuelo.eventualidades"   (anomalía del sistema)
 *
 *  Usa for-comprehension sobre Either para corto-circuitar en
 *  el primer error, igual que Check-in y Dispatcher.
 *
 *  NO conoce Kafka, Postgres ni HTTP — solo los puertos (traits).
 */
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

  // ─── Pasos privados ───────────────────────────────────────

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

  /**
   * Aplica las reglas de negocio de la inspección:
   *   1. RFID no puede estar vacío
   *   2. Peso no puede exceder el máximo
   *
   * Si pasa todas las reglas → Aprobado
   * Si falla cualquiera     → Rechazado (con motivo)
   *
   * Nota: en un sistema real aquí se integraría el escáner
   * de rayos X, detector de metales, etc. Para el MVP
   * las reglas son validaciones de datos.
   */
  private def inspeccionarEquipaje(
                                    cmd: InspeccionCommand
                                  ): Either[SecurityError, ResultadoInspeccion] = {

    // Regla 1: RFID debe ser válido
    if (cmd.codigoRFID.trim.isEmpty)
      return Right(ResultadoInspeccion.Rechazado(
        equipajeId = cmd.equipajeId,
        pasajeroId = cmd.pasajeroId,
        vueloId    = cmd.vueloId,
        motivo     = "Código RFID vacío o inválido"
      ))

    // Regla 2: Peso no puede exceder el máximo
    if (cmd.peso > pesoMaximoKg)
      return Right(ResultadoInspeccion.Rechazado(
        equipajeId = cmd.equipajeId,
        pasajeroId = cmd.pasajeroId,
        vueloId    = cmd.vueloId,
        motivo     = s"Peso ${cmd.peso}kg excede el máximo de ${pesoMaximoKg}kg"
      ))

    // Regla 3: Peso debe ser positivo
    if (cmd.peso <= 0)
      return Right(ResultadoInspeccion.Rechazado(
        equipajeId = cmd.equipajeId,
        pasajeroId = cmd.pasajeroId,
        vueloId    = cmd.vueloId,
        motivo     = s"Peso inválido: ${cmd.peso}kg"
      ))

    // Pasó todas las reglas → Aprobado
    Right(ResultadoInspeccion.Aprobado(
      equipajeId = cmd.equipajeId,
      pasajeroId = cmd.pasajeroId,
      vueloId    = cmd.vueloId
    ))
  }

  /**
   * Persiste el registro de auditoría de la inspección.
   * Se guarda independientemente del resultado (aprobado o rechazado).
   */
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

  /**
   * Publica el evento correcto según el resultado:
   *   - Aprobado  → EventoEquipaje.equipajeAprobado → "equipaje.bodega"
   *   - Rechazado → EventoEquipaje.equipajeRechazado → "vuelo.eventualidades"
   *
   * El Dispatcher escucha "equipaje.bodega" y reacciona solo
   * a los aprobados. Las eventualidades las puede consumir cualquier
   * servicio que necesite saber de anomalías.
   */
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
