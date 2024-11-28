package QuestionFlows

import Common.{Constants, Match, MultiCounter, SingleCounter}
import akka.NotUsed
import akka.stream.scaladsl.{Balance, FileIO, Flow, GraphDSL, Merge}
import akka.stream.{FlowShape, Graph, OverflowStrategy}
import akka.util.ByteString

import java.nio.file.Paths
import java.nio.file.StandardOpenOption.*
import scala.collection.mutable.Map as MutMap


object Question3:
  val flowGraph: Graph[FlowShape[Match, ByteString], NotUsed] =
    Flow.fromGraph(
      GraphDSL.create() {
        implicit builder =>
          import GraphDSL.Implicits.*

          val balance = builder.add(Balance[Match](Constants.balanceSize))
          val merge = builder.add(Merge[MultiCounter](Constants.balanceSize))
          val buffer = Flow[Match].buffer(Constants.bufferSize, OverflowStrategy.backpressure)
          val flowOut = builder.add(Flow[ByteString])

          val filterNonQuarterFinals = builder.add(Flow[Match].filter((m: Match) => m.round <= 4))

          val finalsCounter: Flow[Match, MultiCounter, NotUsed] = Flow[Match]
            .groupBy(Constants.maxSubStreams, (m: Match) => m.win_team.name)
            .map((m: Match) =>
              MultiCounter(m.win_team.name, 1, printFunc)
              + MultiCounter(m.lose_team.name, 1, printFunc)) // Losers have also participated in the finals
            .reduce(_ + _)
            .mergeSubstreams

          val finalsCountMerger = Flow[MultiCounter].reduce(_ + _)

          val toByteString = Flow[MultiCounter].map(w => ByteString(w.toString))

          // First filter out matches not on Sunday, so that the two pipelines gets a more equal amount of work after filtering
          filterNonQuarterFinals ~> balance ~> buffer ~> finalsCounter.async ~> merge ~> finalsCountMerger ~> toByteString ~> flowOut
                                    balance ~> buffer ~> finalsCounter.async ~> merge

          FlowShape(filterNonQuarterFinals.in, flowOut.out)})

  val sink = FileIO.toPath(Paths.get(Constants.pathQ3), Set(CREATE, WRITE, TRUNCATE_EXISTING))

  def printFunc(map: MutMap[String, Int]): String =
    var str: String = ""
    map.foreach((team, count) => str += s"Name: $team --> Amount of participated games at least in quarter-finals: $count\n")
    str