package src.main.scala.security.infraestructure.http

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import src.main.scala.security.application.{InspeccionCommand, InspeccionResponse, SecurityError}

object JsonCodecs {

  implicit val inspeccionCommandDecoder: Decoder[InspeccionCommand] =
    Decoder.forProduct5(
      "equipajeId", "pasajeroId", "vueloId", "codigoRFID", "peso"
    )(InspeccionCommand.apply)

  implicit val inspeccionResponseEncoder: Encoder[InspeccionResponse] =
    Encoder.instance { r =>
      Json.obj(
        "inspeccionId" -> r.inspeccionId.asJson,
        "equipajeId"   -> r.equipajeId.asJson,
        "resultado"    -> r.resultado.asJson,
        "motivo"       -> r.motivo.asJson
      )
    }

  implicit val securityErrorEncoder: Encoder[SecurityError] =
    Encoder.instance { err =>
      val tipo = err match {
        case _: SecurityError.PesoExcedido            => "PesoExcedido"
        case _: SecurityError.CodigoRFIDInvalido      => "CodigoRFIDInvalido"
        case _: SecurityError.EquipajeYaInspeccionado => "EquipajeYaInspeccionado"
        case _: SecurityError.ErrorPersistencia       => "ErrorPersistencia"
        case _: SecurityError.ErrorPublicacion        => "ErrorPublicacion"
      }
      Json.obj(
        "error"   -> tipo.asJson,
        "mensaje" -> err.mensaje.asJson
      )
    }
}
