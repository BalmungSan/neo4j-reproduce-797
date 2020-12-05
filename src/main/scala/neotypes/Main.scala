package neotypes

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.neo4j.{driver => neo4j}
import org.reactivestreams.Publisher

import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

object Main {
  implicit final val ec =
    ExecutionContext.fromExecutorService(
      Executors.newSingleThreadExecutor()
    )

  implicit private val system =
    ActorSystem(
      name = "QuickStart",
      defaultExecutionContext = Some(ec)
    )

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

    def loop(attempts: Int): Future[Boolean] = {
      println()
      println("--------------------------------------------------")
      println(s"Remaining attempts ${attempts}")
      println(s"Metrics: ${driver.metrics.connectionPoolMetrics.asScala}")

      neotypesSession.run("MATCH (p: Person { name: 'Charlize Theron' }) RETURN p.name").flatMap { r =>
        println(s"Results: ${r}")
        if (attempts > 0) loop(attempts - 1)
        else Future.successful(true)
      }
    }

    val app = neotypesSession.run("CREATE (Charlize: Person { name: 'Charlize Theron', born: 1975 })").flatMap { _ =>
      loop(attempts = 1000)
    } recover {
      case NoTransactionError =>
        false

      case ex =>
        println(s"Unexpected error ${ex.getMessage}")
        ex.printStackTrace()
        true
    } flatMap { result =>
      for {
        _ <- neotypesSession.run("MATCH (n) DETACH DELETE n")
        _ <- system.terminate()
      } yield result
    }

    val result = Await.result(app, Duration.Inf)
    println()
    println("-------------------------------------------------")
    println(s"Final metrics: ${driver.metrics.connectionPoolMetrics.asScala}")
    driver.close()
    ec.shutdown()
    println()
    println("-------------------------------------------------")
    if (result) println("------------ It worked! -------------")
    else println("------------ It failed! -------------")
  }
}

final class NeotypesSession (session: neo4j.reactive.RxSession)
                            (implicit ec: ExecutionContext, mat: Materializer) {
  import Syntax._

  def run(query: String): Future[Option[Map[String, String]]] = {
    def runQuery(tx: neo4j.reactive.RxTransaction): Future[Option[Map[String, String]]] =
      tx
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
        }.single

    Future.delegate {
      for {
        tx <- session.beginTransaction.toStream.single.transform(_.flatMap(_.toRight(left = NoTransactionError).toTry))
        result <- runQuery(tx)
        _ <- tx.commit[Unit].toStream.void
      } yield result
    }
  }
}

object Syntax {
  implicit final class PublisherOps[A] (private val publisher: Publisher[A]) extends AnyVal {
    def toStream: Source[A, NotUsed] =
      Source.fromPublisher(publisher)
  }

  implicit final class StreamOps[A] (private val sa: Source[A, NotUsed]) extends AnyVal {
    def list(implicit mat: Materializer): Future[Seq[A]] =
      sa.runWith(Sink.seq)

    def single(implicit mat: Materializer): Future[Option[A]] =
      sa.take(1).runWith(Sink.lastOption)

    def void(implicit mat: Materializer, ec: ExecutionContext): Future[Unit] =
      sa.runWith(Sink.ignore).map(_ => ())
  }
}

object NoTransactionError extends Throwable("Transaction was not created!") with NoStackTrace
