import src.main.scala.security.application.InspeccionUseCase
import src.main.scala.security.infraestructure.config.AppConfig
import src.main.scala.security.infraestructure.kafka.KafkaEventConsumer

import javax.inject.{Inject, Singleton}


@Singleton
class KafkaStarter @Inject()(
                              config:  AppConfig,
                              useCase: InspeccionUseCase
                            ) {
  private val consumer = new KafkaEventConsumer(config, useCase)
  private val thread   = new Thread(consumer, "kafka-consumer-thread")
  thread.setDaemon(true)
  thread.start()
  println("[KafkaStarter] Consumer Kafka iniciado")
}
