import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.sql.SparkSession
import spray.json._

import scala.io.StdIn

// JSON case class and serialization
case class Recommendation(movieId: Int, title: String, rating: Double)

trait JsonSupport extends DefaultJsonProtocol {
  implicit val recommendationFormat = jsonFormat3(Recommendation)
}

object MovieRecommenderApi extends App with JsonSupport {

  // Load config (like .env)
  val config = ConfigFactory.load()
  val ratingsPath = config.getString("app.ratingsPath")
  val moviesPath = config.getString("app.moviesPath")
  val numRecs = config.getInt("app.numRecs")

  // Akka setup
  implicit val system = ActorSystem("movie-recommender")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // Spark setup
  val sparkConf = new SparkConf().setAppName("MovieRecommender").setMaster("local[*]")
  val sc = new SparkContext(sparkConf)
  val spark = SparkSession.builder().getOrCreate()

  // Load and cache ratings
  val ratingsRaw = sc.textFile(ratingsPath)
  val ratingsHeader = ratingsRaw.first()
  val ratings = ratingsRaw
    .filter(_ != ratingsHeader)
    .map(_.split(","))
    .map(arr => Rating(arr(0).toInt, arr(1).toInt, arr(2).toDouble))
    .cache()

  // Load movie titles
  val moviesRaw = sc.textFile(moviesPath)
  val moviesHeader = moviesRaw.first()
  val movieMap = moviesRaw
    .filter(_ != moviesHeader)
    .map(_.split(","))
    .map(arr => (arr(0).toInt, arr(1)))
    .collectAsMap()

  // Train ALS model
  val model = ALS.train(ratings, rank = 10, iterations = 10, lambda = 0.01)

  // Akka route
  val route =
    path("recommendations" / IntNumber) { userId =>
      get {
        try {
          val topRecs = model.recommendProducts(userId, numRecs)
          val result = topRecs.map { r =>
            Recommendation(r.product, movieMap.getOrElse(r.product, "Unknown"), BigDecimal(r.rating).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble)
          }.toList
          complete(result.toJson.prettyPrint)
        } catch {
          case e: Exception =>
            complete(s"""{"error":"User $userId not found or error occurred: ${e.getMessage}"}""")
        }
      }
    }

  println("🚀 MovieRecommender API running at http://localhost:8080/recommendations/{userId}")
  Http().newServerAt("localhost", 8080).bind(route)

  println("🚀 MovieRecommender API running at http://localhost:8080/recommendations/{userId}")

  Http().newServerAt("localhost", 8080).bind(route).foreach { binding =>
    println(s"Server bound to ${binding.localAddress}")
  }
}
