import java.nio.file.{Path, Paths}
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{FlowShape, Graph, IOResult, OverflowStrategy}
import akka.stream.scaladsl.{Balance, FileIO, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.util.ByteString

import java.nio.file.StandardOpenOption.*
import scala.concurrent.{ExecutionContextExecutor, Future}

class Match(val season: Int,
            val round: Int,
            val days_from_epoch: Int,
            val game_date: String,
            val day: String,
            val win_team: Team,
            val lose_team: Team,
            val win_pts: Int,
            val lose_pts: Int,
            val num_ot: Int,
            val academic_year: Int) extends Serializable:
  override def toString: String =
    s"Match(${win_team.name} [$win_pts - $lose_pts] ${lose_team.name})\n"

class Team(val seed: Int,
           val region: String ,
           val market: String ,
           val name: String ,
           val alias: String ,
           val team_id: String ,
           val school_ncaa: String ,
           val code_ncaa: Int,
           val kaggle_team_id: Int) extends Serializable

object Taak1 extends App:

  implicit val actorSystem: ActorSystem = ActorSystem("Taak1")
  implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher

  val resourcesFolder: String = "src/main/resources"
  val pathCSVFile: Path = Paths.get(s"$resourcesFolder/basketball.csv")

  val source: Source[ByteString, Future[IOResult]] = FileIO.fromPath(pathCSVFile)

  val csvParsing: Flow[ByteString, List[ByteString], NotUsed] = CsvParsing.lineScanner()

  val mappingHeader: Flow[List[ByteString], Map[String, ByteString], NotUsed] = CsvToMap.toMap()

  val flowMatch: Flow[Map[String, ByteString], Match, NotUsed] = Flow[Map[String, ByteString]]
    .map(tempMap => {
      tempMap.map(element => {
        (element._1, element._2.utf8String)
      })
    }).map(record => {
      Match(
        season = record("season").toInt,
        round = record("round").toInt,
        days_from_epoch = record("days_from_epoch").toInt,
        game_date = record("game_date"),
        day = record("day"),
        win_team = Team(seed = record("win_seed").toInt,
                        region = record("win_region"),
                        market = record("win_market"),
                        name = record("win_name"),
                        alias = record("win_alias"),
                        team_id = record("win_team_id"),
                        school_ncaa = record("win_school_ncaa"),
                        code_ncaa = record("win_code_ncaa").toInt,
                        kaggle_team_id = record("win_kaggle_team_id").toInt),
        win_pts = record("win_pts").toInt,
        lose_team = Team( seed = record("lose_seed").toInt,
                          region = record("lose_region"),
                          market = record("lose_market"),
                          name = record("lose_name"),
                          alias = record("lose_alias"),
                          team_id = record("lose_team_id"),
                          school_ncaa = record("lose_school_ncaa"),
                          code_ncaa = record("lose_code_ncaa").toInt,
                          kaggle_team_id = record("lose_kaggle_team_id").toInt),
        lose_pts = record("lose_pts").toInt,
        num_ot = record("num_ot").toInt,
        academic_year = record("academic_year").toInt,
      )})

  val flowByteString: Flow[Match, ByteString, NotUsed] = Flow[Match]
    .map(m =>
      println(ByteString(m.toString))
      println("---")
      ByteString(m.toString))


//  val flowOut: Flow[Match, ByteString, NotUsed] = Flow[Match].map(match => {
//    ByteString(s"${match.id},${match.start_station.id},${match.end_station.id}\n")
//  })
//
//  val flowSelectedStations: Graph[FlowShape[Match, ByteString], NotUsed] =
//    Flow.fromGraph(
//      GraphDSL.create() {
//        implicit builder =>
//          import GraphDSL.Implicits._
//
//          val balance = builder.add(Balance[Match](4))
//          val merge = builder.add(Merge[Match](4))
//          val toFlow = builder.add(flowOut)
//
//          val station96Buf = Flow[Match].buffer(10000, OverflowStrategy.fail)
//          val station239Buf = Flow[Match].buffer(100, OverflowStrategy.backpressure)
//          val station234Buf = Flow[Match].buffer(10, OverflowStrategy.dropHead)
//          val station110Buf = Flow[Match].buffer(50, OverflowStrategy.dropTail)
//
//          val station96: Flow[Match, Match, NotUsed] = Flow[Match].filter(match => match.start_station.id == 96 || match.end_station.id == 96)
//          val station239: Flow[Match, Match, NotUsed] = Flow[Match].filter(match => match.start_station.id == 239 || match.end_station.id == 239)
//          val station234: Flow[Match, Match, NotUsed] = Flow[Match].filter(match => match.start_station.id == 234 || match.end_station.id == 234)
//          val station110: Flow[Match, Match, NotUsed] = Flow[Match].filter(match => match.start_station.id == 110 || match.end_station.id == 110)
//
//          balance ~> station96Buf ~> station96 ~> merge
//          balance ~> station239Buf ~> station239 ~> merge
//          balance ~> station234Buf ~> station234 ~> merge
//          balance ~> station110Buf ~> station110 ~> merge ~> toFlow
//
//          FlowShape(balance.in, toFlow.out)
//      }
//    )

  val sink = FileIO.toPath(Paths.get(s"$resourcesFolder/results/taak2_results.txt"), Set(CREATE, WRITE, APPEND))

  //  val sink2 = Sink.foreach(println)

  val runnableGraph: RunnableGraph[Future[IOResult]] =
    source
      .via(csvParsing)
      .via(mappingHeader)
      .via(flowMatch)
//      .via(flowSelectedStations)
      .via(flowByteString)
      .to(sink)

  runnableGraph.run().onComplete(_ => actorSystem.terminate())
