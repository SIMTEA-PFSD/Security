package src.main.scala.security.infraestructure.config

import com.typesafe.config.{Config, ConfigFactory}

final case class DatabaseConfig(
                                 url:      String,
                                 user:     String,
                                 password: String,
                                 driver:   String,
                                 poolSize: Int
                               )

final case class InspeccionConfig(
                                   pesoMaximoKg:       Double,
                                   tiempoInspeccionMs: Int
                                 )

final case class AppConfig(
                            httpPort:              Int,
                            kafkaBootstrap:        String,
                            kafkaTopicIn:          String,   // "registro.pasajero"   — CONSUME
                            kafkaTopicOut:         String,   // "equipaje.bodega"     — PRODUCE (aprobados)
                            kafkaTopicEventualidades: String,// "vuelo.eventualidades"— PRODUCE (rechazados)
                            kafkaGroupId:          String,
                            acks:                  String,
                            db:                    DatabaseConfig,
                            inspeccion:            InspeccionConfig
                          )

object AppConfig {
  def load(): AppConfig = {
    val conf: Config = ConfigFactory.load()
    AppConfig(
      httpPort                 = conf.getInt("security.http.port"),
      kafkaBootstrap           = conf.getString("security.kafka.bootstrap-servers"),
      kafkaTopicIn             = conf.getString("security.kafka.topic-in"),
      kafkaTopicOut            = conf.getString("security.kafka.topic-out"),
      kafkaTopicEventualidades = conf.getString("security.kafka.topic-eventualidades"),
      kafkaGroupId             = conf.getString("security.kafka.group-id"),
      acks                     = conf.getString("security.kafka.acks"),
      db = DatabaseConfig(
        url      = conf.getString("security.db.url"),
        user     = conf.getString("security.db.user"),
        password = conf.getString("security.db.password"),
        driver   = conf.getString("security.db.driver"),
        poolSize = conf.getInt("security.db.pool-size")
      ),
      inspeccion = InspeccionConfig(
        pesoMaximoKg       = conf.getDouble("security.inspeccion.peso-maximo-kg"),
        tiempoInspeccionMs = conf.getInt("security.inspeccion.tiempo-inspeccion-ms")
      )
    )
  }
}
