package neotypes

import cats.effect.{ContextShift, ExitCase, ExitCode, IO, IOApp}
import cats.syntax.all._
import fs2.Stream
import org.neo4j.{driver => neo4j}
import org.reactivestreams.Publisher

import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    IO {
      neo4j.GraphDatabase.driver(
        "bolt://localhost:7687",
        neo4j.Config.builder
          .withoutEncryption
          .withDriverMetrics
          .withLogging(neo4j.Logging.slf4j)
          .build()
      )
    } flatMap { driver =>
      val neotypesDriver = new NeotypesDriver(driver)

      def loop(attempts: Int): IO[Unit] = IO.suspend {
        println()
        println("--------------------------------------------------")
        println(s"Remaining attempts ${attempts}")
        println(s"Metrics: ${driver.metrics.connectionPoolMetrics.asScala}")

        neotypesDriver.run("MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name").flatMap { r =>
          IO.suspend {
            println(s"Results: ${r}")
            if (attempts > 0) loop(attempts - 1)
            else IO.unit
          }
        }
      }

      val setup: IO[Unit] =
        for {
          _ <- neotypesDriver.run("MATCH (n) DETACH DELETE n")
          _ <- neotypesDriver.run("CREATE (Charlize: Person { name: 'Charlize Theron', born: 1975 })")
        } yield ()

      val app = (setup *> loop(attempts = 1000)).recoverWith {
        case NoTransactionError =>
          IO(println(s"Transaction was not created!"))

        case ex => IO {
          println(s"Unexpected error ${ex.getMessage}")
          ex.printStackTrace()
        }
      }

      val program =
        app *>
        IO {
          println()
          println("-------------------------------------------------")
          println(s"Final metrics: ${driver.metrics.connectionPoolMetrics.asScala}")
        } *>
        IO(driver.close())

      program.as(ExitCode.Success)
    }
  }
}

final class NeotypesDriver(driver: neo4j.Driver)
                          (implicit cs: ContextShift[IO]) {
  import Syntax._

  def run(query: String): IO[Option[Map[String, String]]] = {
    val txIO = for {
      s <- IO(driver.rxSession)
      opt <- s.beginTransaction.toStream.toIO
      tx <- IO.fromOption(opt)(orElse = NoTransactionError)
    } yield tx -> s

    val result = Stream.bracketCase(txIO) {
      case ((tx, s), ExitCase.Completed) => tx.commit[Unit].toStream.toIOUnit *> s.close[Unit].toStream.toIOUnit
      case ((tx, s), _)                  => tx.rollback[Unit].toStream.toIOUnit *> s.close[Unit].toStream.toIOUnit
    } flatMap { tx =>
      tx
        ._1
        .run(query)
        .records
        .toStream
        .map { record =>
          record
            .fields
            .asScala
            .iterator
            .map(p => p.key -> p.value.toString)
            .toMap
        }
    }

    result.toIO
  }
}

object Syntax {
  implicit final class PublisherOps[A] (private val publisher: Publisher[A]) extends AnyVal {
    def toStream(implicit cs: ContextShift[IO]): Stream[IO, A] =
      fs2.interop.reactivestreams.fromPublisher[IO, A](publisher)
  }

  implicit final class StreamOps[A] (private val stream: Stream[IO, A]) {
    def toIO: IO[Option[A]] =
      stream.take(1).compile.last

    def toIOUnit: IO[Unit] =
      stream.compile.drain
  }
}

object NoTransactionError extends Throwable("Transaction was not created!") with NoStackTrace
