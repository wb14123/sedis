package me.binwang.sedis

import RedisProtocol._

import scala.util.{Failure, Success, Try}

class RedisDecoder extends NetDecoder[RedisData] {

  private var buffer = Array[Byte]()
  private var arrayLen: Option[Int] = None
  private var bulkLen: Option[Int] = None
  private var childData: Seq[RedisData] = Seq()
  private var childDecoder: Option[RedisDecoder] = None

  private def clear() = {
    buffer = Array[Byte]()
    arrayLen = None
    bulkLen = None
    childData = Seq()
    childDecoder = None
  }

  private implicit class redisBytes(data: Array[Byte]) {
    // remove \r\n in the end
    def trimEnd: String = new String(data.take(data.length - 2))
  }

  /**
    * When we meet "\r\n", use this method to check if we can get a reasonable result.
    *
    * @return The result if the data has been resolved.
    */
  private def tryDecode(): Option[RedisData] = {
    if (bulkLen.nonEmpty) {
      if (bulkLen.get == buffer.length - 2) {
        Some(RedisString(buffer.trimEnd))
      } else {
        None
      }
    } else {
      buffer.head match {
        case '+' => Some(RedisString(buffer.tail.trimEnd))
        case '-' => Some(RedisError(buffer.tail.trimEnd))
        case ':' => Some(RedisInteger(buffer.tail.trimEnd.toInt))
        case '$' =>
          bulkLen = Some(buffer.tail.trimEnd.toInt)
          buffer = Array[Byte]()
          None
        case '*' =>
          arrayLen = Some(buffer.tail.trimEnd.toInt)
          childDecoder = Some(new RedisDecoder)
          buffer = Array[Byte]()
          None
      }
    }
  }

  override def send(byte: Byte): Try[Option[RedisData]] = {
    if (arrayLen.nonEmpty) {
      childDecoder.get.send(byte) match {
        case Success(Some(result)) =>
          childData :+= result
          if (childData.length == arrayLen.get) {
            Success(Some(RedisArray(childData)))
          } else {
            Success(None)
          }
        case other => other
      }
    } else {
      buffer :+= byte
      if (buffer.endsWith(Array('\r', '\n'))) {
        Try(tryDecode())
      } else {
        Success(None)
      }
    } match {
      case Success(None) => Success(None)
      case other => clear(); other
    }
  }
}
