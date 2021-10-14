package io.janstenpickle.trace4cats.newrelic

import cats.effect.kernel.{Async, Resource}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import org.http4s.client.Client
import org.http4s.blaze.client.BlazeClientBuilder

import scala.concurrent.ExecutionContext

object NewRelicSpanCompleter {
  def blazeClient[F[_]: Async](
    process: TraceProcess,
    apiKey: String,
    endpoint: Endpoint,
    config: CompleterConfig = CompleterConfig(),
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanCompleter[F]] = for {
    client <- ec.fold(BlazeClientBuilder[F])(BlazeClientBuilder[F].withExecutionContext).resource
    completer <- apply[F](client, process, apiKey, endpoint, config)
  } yield completer

  def apply[F[_]: Async](
    client: Client[F],
    process: TraceProcess,
    apiKey: String,
    endpoint: Endpoint,
    config: CompleterConfig = CompleterConfig()
  ): Resource[F, SpanCompleter[F]] =
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      Resource
        .eval(NewRelicSpanExporter[F, Chunk](client, apiKey, endpoint))
        .flatMap(QueuedSpanCompleter[F](process, _, config))
    }
}
