import Taak1.resourcesFolder
import akka.NotUsed
import akka.stream.scaladsl.{Balance, FileIO, Flow, GraphDSL, Merge}
import akka.stream.{FlowShape, Graph, OverflowStrategy}
import akka.util.ByteString

import java.nio.file.Paths
import java.nio.file.StandardOpenOption.*
import scala.collection.mutable.Map as MutMap


object Question4:
  val flow: Flow[Match, ByteString, NotUsed] = Flow[Match]
    .filter((m: Match) => 1980 <= m.academic_year && m.academic_year <= 1990)
    .fold(0)((c: Int, m: Match) => c + 1) // each matchup has exactly 1 losing team
    .map(c => ByteString(s"Amount of losses between 1980 and 1990: ${c.toString}"))

  val flowBalanced: Graph[FlowShape[Match, ByteString], NotUsed] =
    Flow.fromGraph(
      GraphDSL.create() {
        implicit builder =>
          import GraphDSL.Implicits.*

          val balance = builder.add(Balance[Match](2))
          val merge = builder.add(Merge[Int](2))
          val flowOut = builder.add(Flow[ByteString])

          val filterNonQuarterFinals = Flow[Match].filter((m: Match) => m.round <= 4)
          val finalsCounter = Flow[Match].fold(0)((c: Int, m: Match) => c + 2) // 2 teams (win_team and lose_team) play in each match
          val finalsCountMerger = Flow[Int].reduce(_ + _)
          val toByteString = Flow[Int].map(w => ByteString(w.toString))

          val buffer = Flow[Match].buffer(20, OverflowStrategy.backpressure)

          balance ~> buffer ~> filterNonQuarterFinals.async ~> finalsCounter.async ~> merge ~> finalsCountMerger ~> toByteString ~> flowOut
          balance ~> buffer ~> filterNonQuarterFinals.async ~> finalsCounter.async ~> merge

          FlowShape(balance.in, flowOut.out)})

  val sink = FileIO.toPath(Paths.get(s"$resourcesFolder/results/Q4.txt"), Set(CREATE, WRITE, TRUNCATE_EXISTING))

