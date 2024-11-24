import scala.collection.mutable.Map as MutMap


class MatchCounter(val map: MutMap[String, Int], val printFunc: MutMap[String, Int] => String):
  def this(team_name: String, count: Int, printFunc: MutMap[String, Int] => String) = 
    this(MutMap((team_name, count)), printFunc)

  def +(that: MatchCounter): MatchCounter =
    val temp: MutMap[String, Int] = this.map.clone()
    for key <- that.map.keys do
      val thatCount: Int = that.map(key)
      temp.get(key) match
        case Some(thisCount) => temp(key) = thatCount + thisCount
        case None => temp.addOne(key, thatCount)
    new MatchCounter(temp, printFunc)

  override def toString: String = printFunc(this.map)
