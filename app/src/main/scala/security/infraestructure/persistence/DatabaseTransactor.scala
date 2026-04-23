package src.main.scala.security.infraestructure.persistence

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import src.main.scala.security.infraestructure.config.DatabaseConfig

/**
 * Construye el pool de conexiones Postgres como Resource[IO].
 * Al cerrarse la app (Ctrl+C) el pool se libera automáticamente.
 *
 * Mismo patrón que DatabaseTransactor en Check-in y Dispatcher.
 */
object DatabaseTransactor {

  def build(config: DatabaseConfig): Resource[IO, HikariTransactor[IO]] = {
    val hc = new HikariConfig()
    hc.setDriverClassName(config.driver)
    hc.setJdbcUrl(config.url)
    hc.setUsername(config.user)
    hc.setPassword(config.password)
    hc.setMaximumPoolSize(config.poolSize)
    hc.setPoolName("security-pool")

    HikariTransactor.fromHikariConfig[IO](hc)
  }
}
