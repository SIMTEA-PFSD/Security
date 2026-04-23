package src.main.scala.security.infraestructure.http


import cats.effect.IO
import io.circe.Json
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.io._
import io.circe.generic.auto._
import src.main.scala.security.application.{InspeccionCommand, InspeccionUseCase, SecurityError}
import src.main.scala.security.domain.ports.InspeccionRepository

class SecurityRoutes(
                      useCase:        InspeccionUseCase,
                      inspeccionRepo: InspeccionRepository
                    ) {
  import JsonCodecs._

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case GET -> Root / "health" =>
      Ok(Json.obj(
        "status"  -> "UP".asJson,
        "service" -> "security".asJson
      ))

    case req @ POST -> Root / "api" / "v1" / "inspeccionar" =>
      req.attemptAs[InspeccionCommand].value.flatMap {
        case Left(err) =>
          BadRequest(Json.obj(
            "error"   -> "ErrorDeserializacion".asJson,
            "mensaje" -> err.getMessage.asJson
          ))
        case Right(cmd) =>
          IO.blocking(useCase.ejecutar(cmd)).flatMap {
            case Right(resp) =>
              if (resp.resultado == "APROBADO") Created(resp.asJson)
              else Ok(resp.asJson)

            case Left(e: SecurityError.EquipajeYaInspeccionado) =>
              Conflict((e: SecurityError).asJson)

            case Left(e) =>
              val json = (e: SecurityError).asJson
              e match {
                case _: SecurityError.ErrorPublicacion  => InternalServerError(json)
                case _: SecurityError.ErrorPersistencia => InternalServerError(json)
                case _                                  => BadRequest(json)
              }
          }
      }

    case GET -> Root / "api" / "v1" / "inspecciones" =>
      IO.blocking(inspeccionRepo.listarTodas()).flatMap { inspecciones =>
        val json = Json.arr(inspecciones.map { i =>
          Json.obj(
            "id"         -> i.id.asJson,
            "equipajeId" -> i.equipajeId.asJson,
            "pasajeroId" -> i.pasajeroId.asJson,
            "vueloId"    -> i.vueloId.asJson,
            "codigoRFID" -> i.codigoRFID.asJson,
            "peso"       -> i.peso.asJson,
            "resultado"  -> i.resultado.nombre.asJson,
            "motivo"     -> i.motivo.asJson,
            "timestamp"  -> i.timestamp.toString.asJson
          )
        }: _*)
        Ok(json)
      }

    case GET -> Root / "api" / "v1" / "inspecciones" / "vuelo" / vueloId =>
      IO.blocking(inspeccionRepo.listarPorVuelo(vueloId)).flatMap { inspecciones =>
        val aprobados  = inspecciones.count(_.resultado.nombre == "APROBADO")
        val rechazados = inspecciones.count(_.resultado.nombre == "RECHAZADO")
        val json = Json.obj(
          "vueloId"    -> vueloId.asJson,
          "total"      -> inspecciones.size.asJson,
          "aprobados"  -> aprobados.asJson,
          "rechazados" -> rechazados.asJson,
          "inspecciones" -> Json.arr(inspecciones.map { i =>
            Json.obj(
              "equipajeId" -> i.equipajeId.asJson,
              "resultado"  -> i.resultado.nombre.asJson,
              "motivo"     -> i.motivo.asJson,
              "timestamp"  -> i.timestamp.toString.asJson
            )
          }: _*)
        )
        Ok(json)
      }

    case GET -> Root / "api" / "v1" / "inspecciones" / "equipaje" / equipajeId =>
      IO.blocking(inspeccionRepo.buscarPorEquipaje(equipajeId)).flatMap {
        case None =>
          NotFound(Json.obj(
            "error"   -> "NoEncontrado".asJson,
            "mensaje" -> s"No se encontró inspección para equipaje $equipajeId".asJson
          ))
        case Some(i) =>
          Ok(Json.obj(
            "id"         -> i.id.asJson,
            "equipajeId" -> i.equipajeId.asJson,
            "pasajeroId" -> i.pasajeroId.asJson,
            "vueloId"    -> i.vueloId.asJson,
            "resultado"  -> i.resultado.nombre.asJson,
            "motivo"     -> i.motivo.asJson,
            "timestamp"  -> i.timestamp.toString.asJson
          ))
      }
  }
}
