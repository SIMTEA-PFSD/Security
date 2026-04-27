package controllers


import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import src.main.scala.security.application.{InspeccionCommand, InspeccionUseCase, SecurityError}
import src.main.scala.security.domain.ports.InspeccionRepository


@Singleton
class SecurityController @Inject()(
                                    cc:             ControllerComponents,
                                    useCase:        InspeccionUseCase,
                                    inspeccionRepo: InspeccionRepository
                                  ) extends AbstractController(cc) {

  // GET /health
  def health() = Action {
    Ok(Json.obj("status" -> "UP", "service" -> "security"))
  }

  // POST /api/v1/inspeccionar
  def inspeccionar() = Action(parse.json) { request =>
    val json = request.body
    (for {
      equipajeId <- (json \ "equipajeId").asOpt[String]
      pasajeroId <- (json \ "pasajeroId").asOpt[String]
      vueloId    <- (json \ "vueloId").asOpt[String]
      codigoRFID <- (json \ "codigoRFID").asOpt[String]
      peso       <- (json \ "peso").asOpt[Double]
    } yield InspeccionCommand(equipajeId, pasajeroId, vueloId, codigoRFID, peso)) match {

      case None =>
        BadRequest(Json.obj("error" -> "CamposFaltantes",
          "mensaje" -> "equipajeId, pasajeroId, vueloId, codigoRFID y peso son requeridos"))

      case Some(cmd) =>
        useCase.ejecutar(cmd) match {
          case Right(resp) =>
            val json = Json.obj(
              "inspeccionId" -> resp.inspeccionId,
              "equipajeId"   -> resp.equipajeId,
              "resultado"    -> resp.resultado,
              "motivo"       -> resp.motivo
            )
            if (resp.resultado == "APROBADO") Created(json) else Ok(json)

          case Left(e: SecurityError.EquipajeYaInspeccionado) =>
            Conflict(Json.obj("error" -> "EquipajeYaInspeccionado", "mensaje" -> e.mensaje))

          case Left(e: SecurityError.ErrorPublicacion) =>
            InternalServerError(Json.obj("error" -> "ErrorPublicacion", "mensaje" -> e.mensaje))

          case Left(e: SecurityError.ErrorPersistencia) =>
            InternalServerError(Json.obj("error" -> "ErrorPersistencia", "mensaje" -> e.mensaje))

          case Left(e) =>
            BadRequest(Json.obj("error" -> "ErrorValidacion", "mensaje" -> e.mensaje))
        }
    }
  }

  // GET /api/v1/inspecciones
  def listarInspecciones() = Action {
    val inspecciones = inspeccionRepo.listarTodas()
    Ok(Json.arr(inspecciones.map { i =>
      Json.obj(
        "id"         -> i.id,
        "equipajeId" -> i.equipajeId,
        "pasajeroId" -> i.pasajeroId,
        "vueloId"    -> i.vueloId,
        "resultado"  -> i.resultado.nombre,
        "motivo"     -> i.motivo,
        "timestamp"  -> i.timestamp.toString
      )
    }))
  }

  // GET /api/v1/inspecciones/vuelo/:vueloId
  def listarPorVuelo(vueloId: String) = Action {
    val inspecciones = inspeccionRepo.listarPorVuelo(vueloId)
    val aprobados  = inspecciones.count(_.resultado.nombre == "APROBADO")
    val rechazados = inspecciones.count(_.resultado.nombre == "RECHAZADO")
    Ok(Json.obj(
      "vueloId"    -> vueloId,
      "total"      -> inspecciones.size,
      "aprobados"  -> aprobados,
      "rechazados" -> rechazados,
      "inspecciones" -> Json.arr(inspecciones.map { i =>
        Json.obj(
          "equipajeId" -> i.equipajeId,
          "resultado"  -> i.resultado.nombre,
          "motivo"     -> i.motivo,
          "timestamp"  -> i.timestamp.toString
        )
      })
    ))
  }

  // GET /api/v1/inspecciones/equipaje/:equipajeId
  def buscarPorEquipaje(equipajeId: String) = Action {
    inspeccionRepo.buscarPorEquipaje(equipajeId) match {
      case None =>
        NotFound(Json.obj("error" -> "NoEncontrado",
          "mensaje" -> s"No se encontró inspección para equipaje $equipajeId"))
      case Some(i) =>
        Ok(Json.obj(
          "id"         -> i.id,
          "equipajeId" -> i.equipajeId,
          "pasajeroId" -> i.pasajeroId,
          "vueloId"    -> i.vueloId,
          "resultado"  -> i.resultado.nombre,
          "motivo"     -> i.motivo,
          "timestamp"  -> i.timestamp.toString
        ))
    }
  }
}
