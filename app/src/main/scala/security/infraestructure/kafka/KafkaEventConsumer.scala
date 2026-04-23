package src.main.scala.security.infraestructure.kafka

import io.circe.parser._
import io.circe.{Decoder, HCursor}
import org.apache.kafka.clients.consumer.{ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import src.main.scala.security.application.{InspeccionCommand, InspeccionUseCase, SecurityError}
import src.main.scala.security.infraestructure.config.AppConfig

import java.time.Duration
import java.util.{Collections, Properties}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/**
 * «adapter» — Consumidor del tópico "registro.pasajero".
 *
 * Escucha los eventos PasajeroRegistrado que publica Check-in
 * y por cada uno ejecuta el InspeccionUseCase.
 *
 * ─────────────────────────────────────────────────────────────
 *  Formato del JSON que llega de Check-in:
 * ─────────────────────────────────────────────────────────────
 * {
 *   "eventId":    "uuid",
 *   "tipo":       "PasajeroRegistrado",
 *   "equipajeId": "equip-001",
 *   "timestamp":  "2024-01-15T10:30:00Z",
 *   "topico":     "registro.pasajero",
 *   "payload": {
 *     "pasajeroId": "pas-123",
 *     "vueloId":    "AV-456",
 *     "equipajeId": "equip-001"
 *   }
 * }
 * ─────────────────────────────────────────────────────────────
 *
 * PROBLEMA: Check-in no incluye codigoRFID ni peso en el payload
 * del evento PasajeroRegistrado (solo manda pasajeroId, vueloId,
 * equipajeId). Para el MVP simulamos esos valores. En producción
 * el escáner RFID físico los proveería via HTTP al endpoint
 * POST /api/v1/inspeccionar.
 *
 * Corre en un hilo daemon separado del servidor HTTP.
 */
class KafkaEventConsumer(
                          config:  AppConfig,
                          useCase: InspeccionUseCase
                        ) extends Runnable {

  private val props = new Properties()
  props.put("bootstrap.servers",  config.kafkaBootstrap)
  props.put("group.id",           config.kafkaGroupId)
  props.put("key.deserializer",   classOf[StringDeserializer].getName)
  props.put("value.deserializer", classOf[StringDeserializer].getName)
  props.put("auto.offset.reset",  "earliest")
  props.put("enable.auto.commit", "false")  // commit manual

  private val consumer = new KafkaConsumer[String, String](props)

  @volatile private var corriendo = true

  override def run(): Unit = {
    consumer.subscribe(Collections.singletonList(config.kafkaTopicIn))
    println(s"[Consumer] Suscrito a tópico: ${config.kafkaTopicIn}")

    try {
      while (corriendo) {
        val records: ConsumerRecords[String, String] =
          consumer.poll(Duration.ofMillis(500))

        records.asScala.foreach { record =>
          println(s"[Consumer] Mensaje recibido | offset=${record.offset()} key=${record.key()}")
          procesarMensaje(record.value())
        }

        if (!records.isEmpty) consumer.commitSync()
      }
    } catch {
      case NonFatal(ex) =>
        println(s"[Consumer] Error en polling: ${ex.getMessage}")
    } finally {
      consumer.close()
      println("[Consumer] Consumer cerrado.")
    }
  }

  def detener(): Unit = {
    corriendo = false
    consumer.wakeup()
  }

  // ─── Procesamiento de cada mensaje ────────────────────────

  private def procesarMensaje(json: String): Unit =
    deserializarComando(json) match {
      case Left(err) =>
        println(s"[Consumer] ✗ Error deserializando mensaje: $err")
        println(s"[Consumer]   JSON recibido: ${json.take(200)}")

      case Right(cmd) =>
        println(s"[Consumer] → Inspeccionando equipaje=${cmd.equipajeId} vuelo=${cmd.vueloId}")
        useCase.ejecutar(cmd) match {
          case Right(resp) =>
            println(
              s"[Consumer] ✓ Inspección completada | " +
                s"equipaje=${resp.equipajeId} resultado=${resp.resultado}" +
                resp.motivo.map(m => s" motivo=$m").getOrElse("")
            )
          case Left(err: SecurityError.EquipajeYaInspeccionado) =>
            // No es un error crítico — Kafka puede re-entregar mensajes
            println(s"[Consumer] ⚠ ${err.mensaje} — ignorando (idempotente)")
          case Left(err) =>
            println(s"[Consumer] ✗ ${err.mensaje}")
        }
    }

  /**
   * Deserializa el JSON de Kafka al InspeccionCommand.
   *
   * El JSON tiene la forma del EventoEquipaje publicado por Check-in.
   * codigoRFID y peso se leen del payload si existen, o se simulan
   * para el MVP (en producción el escáner los provee por HTTP).
   */
  private def deserializarComando(json: String): Either[String, InspeccionCommand] = {
    implicit val decoder: Decoder[InspeccionCommand] = (c: HCursor) =>
      for {
        equipajeId <- c.downField("equipajeId").as[String]
        pasajeroId <- c.downField("payload").downField("pasajeroId").as[String]
        vueloId    <- c.downField("payload").downField("vueloId").as[String]
        // codigoRFID y peso: si no están en el payload, usamos valores simulados
        codigoRFID = c.downField("payload").downField("codigoRFID")
          .as[String].getOrElse(s"RFID-AUTO-$equipajeId")
        peso       = c.downField("payload").downField("peso")
          .as[Double].getOrElse(10.0)
      } yield InspeccionCommand(equipajeId, pasajeroId, vueloId, codigoRFID, peso)

    parse(json)
      .flatMap(_.as[InspeccionCommand])
      .left.map(_.getMessage)
  }
}