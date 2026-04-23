package src.main.scala.security.domain.ports

import src.main.scala.security.domain.events.EventoEquipaje

/**
 * Puerto de salida: publicación de eventos al event bus.
 * Mismo patrón que Check-in y Dispatcher.
 */
trait EventPublisher {
  def publicar(evento: EventoEquipaje): Either[String, Unit]
  def cerrar(): Unit
}
