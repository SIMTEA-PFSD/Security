package src.main.scala.security.domain.events


import java.time.Instant
import java.util.UUID

/**
 * «value object» — Payload que viaja por Kafka.
 *
 * Misma estructura que en Check-in y Dispatcher para que el esquema
 * JSON sea consistente en todos los tópicos del sistema.
 */
final case class EventoEquipaje(
                                 eventId:    UUID,
                                 tipo:       TipoEvento,
                                 equipajeId: String,
                                 timestamp:  Instant,
                                 topico:     String,
                                 payload:    Map[String, String]
                               )

object EventoEquipaje {

  /**
   * Evento que Security publica cuando un equipaje PASA la inspección.
   * Este es el evento que el Dispatcher consume de "equipaje.bodega".
   *
   * El payload incluye vueloId porque el Dispatcher lo necesita para
   * deserializar el DispatchCommand (ver KafkaEventConsumer del Dispatcher).
   */
  def equipajeAprobado(
                        equipajeId: String,
                        pasajeroId: String,
                        vueloId:    String
                      ): EventoEquipaje =
    EventoEquipaje(
      eventId    = UUID.randomUUID(),
      tipo       = TipoEvento.EquipajeAsignado,
      equipajeId = equipajeId,
      timestamp  = Instant.now(),
      topico     = TipoEvento.EquipajeAsignado.topico,
      payload    = Map(
        "equipajeId" -> equipajeId,
        "pasajeroId" -> pasajeroId,
        "vueloId"    -> vueloId
      )
    )

  /**
   * Evento que Security publica cuando un equipaje FALLA la inspección.
   * Va al tópico "vuelo.eventualidades" para que otros servicios reaccionen.
   */
  def equipajeRechazado(
                         equipajeId: String,
                         pasajeroId: String,
                         vueloId:    String,
                         motivo:     String
                       ): EventoEquipaje =
    EventoEquipaje(
      eventId    = UUID.randomUUID(),
      tipo       = TipoEvento.EventualidadDetectada,
      equipajeId = equipajeId,
      timestamp  = Instant.now(),
      topico     = TipoEvento.EventualidadDetectada.topico,
      payload    = Map(
        "equipajeId" -> equipajeId,
        "pasajeroId" -> pasajeroId,
        "vueloId"    -> vueloId,
        "motivo"     -> motivo
      )
    )

  /**
   * Evento de log interno — registra que el escáner RFID procesó el equipaje.
   * Va al tópico "equipaje.seguridad" (log de auditoría).
   */
  def equipajeEscaneado(
                         equipajeId: String,
                         codigoRFID: String,
                         peso:       Double
                       ): EventoEquipaje =
    EventoEquipaje(
      eventId    = UUID.randomUUID(),
      tipo       = TipoEvento.EquipajeEscaneado,
      equipajeId = equipajeId,
      timestamp  = Instant.now(),
      topico     = TipoEvento.EquipajeEscaneado.topico,
      payload    = Map(
        "equipajeId" -> equipajeId,
        "codigoRFID" -> codigoRFID,
        "peso"       -> peso.toString
      )
    )
}
