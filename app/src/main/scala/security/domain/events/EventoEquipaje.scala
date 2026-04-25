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
