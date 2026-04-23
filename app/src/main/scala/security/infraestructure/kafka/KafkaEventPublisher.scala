package src.main.scala.security.infraestructure.kafka

import io.circe.Json
import io.circe.syntax._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import src.main.scala.security.domain.events.EventoEquipaje
import src.main.scala.security.domain.ports.EventPublisher
import src.main.scala.security.infraestructure.config.AppConfig

import java.util.Properties
import scala.util.{Failure, Success, Try}

/**
 * «adapter» — Publicador de eventos a Kafka.
 *
 * Security produce a DOS tópicos distintos según el resultado:
 *   - "equipaje.bodega"       → equipaje aprobado (lo consume Dispatcher)
 *   - "vuelo.eventualidades"  → equipaje rechazado (anomalía)
 *
 * El tópico correcto ya viene dentro del EventoEquipaje (campo `topico`),
 * así que este adapter simplemente serializa y envía — sin lógica de negocio.
 *
 * La key del mensaje es equipajeId → garantiza orden por equipaje
 * en la partición de Kafka.
 */
class KafkaEventPublisher(config: AppConfig) extends EventPublisher {

  private val props = new Properties()
  props.put("bootstrap.servers", config.kafkaBootstrap)
  props.put("key.serializer",    classOf[StringSerializer].getName)
  props.put("value.serializer",  classOf[StringSerializer].getName)
  props.put("acks",              config.acks)
  props.put("retries",           "3")
  props.put("client.id",         "security-service-producer")

  private val producer = new KafkaProducer[String, String](props)

  override def publicar(evento: EventoEquipaje): Either[String, Unit] = {
    val json   = serializarEvento(evento).noSpaces
    val record = new ProducerRecord[String, String](
      evento.topico,       // el tópico correcto viene en el evento
      evento.equipajeId,   // key → misma partición para el mismo equipaje
      json
    )

    Try(producer.send(record).get()) match {
      case Success(_)  =>
        println(s"[Kafka] ✓ Evento publicado → ${evento.topico} | ${evento.tipo}")
        Right(())
      case Failure(ex) =>
        Left(s"Fallo enviando a Kafka: ${ex.getMessage}")
    }
  }

  override def cerrar(): Unit = {
    producer.flush()
    producer.close()
  }

  /**
   * Serialización manual con Circe — sin macros, igual que Check-in y Dispatcher.
   * El JSON resultante es exactamente el que los otros servicios esperan deserializar.
   */
  private def serializarEvento(e: EventoEquipaje): Json = {
    val payloadJson = Json.obj(
      e.payload.view.mapValues(Json.fromString).toSeq: _*
    )
    Json.obj(
      "eventId"    -> e.eventId.toString.asJson,
      "tipo"       -> e.tipo.toString.asJson,
      "equipajeId" -> e.equipajeId.asJson,
      "timestamp"  -> e.timestamp.toString.asJson,
      "topico"     -> e.topico.asJson,
      "payload"    -> payloadJson
    )
  }
}
