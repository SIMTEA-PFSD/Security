package src.main.scala.security.domain.ports

import src.main.scala.security.domain.model.InspeccionEquipaje

/**
 * Puerto de salida: persistencia de inspecciones.
 * El dominio define QUÉ necesita, la infraestructura define CÓMO.
 */
trait InspeccionRepository {
  def guardar(i: InspeccionEquipaje): Either[String, InspeccionEquipaje]
  def buscarPorEquipaje(equipajeId: String): Option[InspeccionEquipaje]
  def listarTodas(): List[InspeccionEquipaje]
  def listarPorVuelo(vueloId: String): List[InspeccionEquipaje]
}
