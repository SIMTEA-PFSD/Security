import cats.effect.unsafe.implicits.global
import com.google.inject.{AbstractModule, Provides, Singleton}
import src.main.scala.security.application.InspeccionUseCase
import src.main.scala.security.domain.ports.InspeccionRepository
import src.main.scala.security.infraestructure.config.AppConfig
import src.main.scala.security.infraestructure.kafka.KafkaEventPublisher
import src.main.scala.security.infraestructure.persistence.{DatabaseTransactor, PostgresInspeccionRepository}

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[KafkaStarter]).asEagerSingleton()
    bind(classOf[InspeccionRepository])
      .to(classOf[PostgresInspeccionRepository])
  }

  @Provides @Singleton
  def provideConfig(): AppConfig = AppConfig.load()

  @Provides @Singleton
  def providePublisher(config: AppConfig): KafkaEventPublisher =
    new KafkaEventPublisher(config)

  @Provides @Singleton
  def provideRepository(config: AppConfig): PostgresInspeccionRepository = {
    val (xa, _) = DatabaseTransactor.build(config.db).allocated.unsafeRunSync()
    new PostgresInspeccionRepository(xa)
  }

  @Provides @Singleton
  def provideUseCase(
                      repo:      PostgresInspeccionRepository,
                      publisher: KafkaEventPublisher,
                      config:    AppConfig
                    ): InspeccionUseCase =
    new InspeccionUseCase(repo, publisher, config.inspeccion.pesoMaximoKg)
}