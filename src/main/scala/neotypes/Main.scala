package neotypes

import cats.effect.{ContextShift, IO}
import fs2.Stream
import org.neo4j.{driver => neo4j}
import org.reactivestreams.Publisher

import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

object Main {
  implicit val ec =
    ExecutionContext.fromExecutorService(
      Executors.newSingleThreadExecutor()
    )

  implicit val cs =
    IO.contextShift(ec)

  def main(args: Array[String]): Unit = {
    val driver =
      neo4j.GraphDatabase.driver(
        "bolt://localhost:7687",
        neo4j.Config.builder
          .withoutEncryption
          .withDriverMetrics
          .withLogging(neo4j.Logging.slf4j)
          .build()
      )

    val neotypesSession = new NeotypesSession(driver.rxSession)

    def loop(attempts: Int): Future[Unit] = {
      println()
      println("--------------------------------------------------")
      println(s"Remaining attempts ${attempts}")
      println(s"Metrics: ${driver.metrics.connectionPoolMetrics.asScala}")

      neotypesSession.run("MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name").flatMap { r =>
        println(s"Results: ${r}")
        if (attempts > 0) loop(attempts - 1)
        else Future.unit
      }
    }

    def setup: Future[Unit] =
      for {
        _ <- neotypesSession.run("MATCH (n) DETACH DELETE n")
        _ <- neotypesSession.run("CREATE (Charlize: Person { name: 'Charlize Theron', born: 1975 })")
      } yield ()

    val app = setup.flatMap { _ =>
      loop(attempts = 1000)
    } recover {
      case NoTransactionError =>
        println(s"Transaction was not created!")

      case ex =>
        println(s"Unexpected error ${ex.getMessage}")
        ex.printStackTrace()
    }

    Await.ready(app, Duration.Inf)
    println()
    println("-------------------------------------------------")
    println(s"Final metrics: ${driver.metrics.connectionPoolMetrics.asScala}")
    driver.close()
    ec.shutdown()
  }
}

final class NeotypesSession (session: neo4j.reactive.RxSession)
                            (implicit cs: ContextShift[IO]) {
  import Syntax._

  def run(query: String): Future[Option[Map[String, String]]] = {
    def runQuery(tx: neo4j.reactive.RxTransaction): IO[Option[Map[String, String]]] =
      tx
        .run(query)
        .records
        .toIO
        .map { recordOption =>
          recordOption.map { record =>
            record
              .fields
              .asScala
              .iterator
              .map(p => p.key -> p.value.toString)
              .toMap
          }
        }

    val io = for {
      tx <- session.beginTransaction.toIO.flatMap(o => IO.fromOption(o)(orElse = NoTransactionError))
      result <- runQuery(tx)
      _ <- tx.commit[Unit].toIO
    } yield result

    io.unsafeToFuture()
  }
}

object Syntax {
  implicit final class PublisherOps[A] (private val publisher: Publisher[A]) extends AnyVal {
    def toIO(implicit cs: ContextShift[IO]): IO[Option[A]] =
      fs2.interop.reactivestreams.fromPublisher[IO, A](publisher).take(1).compile.last
  }
}

object NoTransactionError extends Throwable("Transaction was not created!") with NoStackTrace
