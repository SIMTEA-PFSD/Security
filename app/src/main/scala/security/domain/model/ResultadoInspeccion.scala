package src.main.scala.security.domain.model

/**
 * Usamos sealed trait para que el compilador obligue a cubrir
 * todos los casos en los pattern match.
 */

sealed trait ResultadoInspeccion

object ResultadoInspeccion {
  final case class Aprobado(
                             equipajeId: String,
                             pasajeroId: String,
                             vueloId:    String
                           ) extends ResultadoInspeccion

  final case class Rechazado(
                              equipajeId: String,
                              pasajeroId: String,
                              vueloId:    String,
                              motivo:     String
                            ) extends ResultadoInspeccion
}
