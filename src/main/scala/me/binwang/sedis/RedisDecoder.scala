package me.binwang.sedis

import RedisProtocol._

import scala.util.{Failure, Success, Try}

class RedisDecoder extends NetDecoder[RedisData] {

  private var buffer = Array[Byte]()
  private var arrayLen: Option[Int] = None
  private var bulkLen: Option[Int] = None
  private var arrays: Seq[RedisData] = Seq()

  private def clear() = {
    buffer = Array[Byte]()
    arrayLen = None
    bulkLen = None
    arrays = Seq()
  }

  /**
    * When we meet "\r\n", use this method to check if we can get a reasonable result.
    *
    * @return The result if the data has been resolved.
    */
  private def tryDecode(): Option[RedisData] = {

    // TODO: nested array

    def tail2str() = {
      new String(buffer.tail.take(buffer.length - 3))
    }

    val result =
      if (bulkLen.nonEmpty) {
        if (buffer.length - 2 == bulkLen.get) {
          bulkLen = None
          Some(RedisString(new String(buffer.take(buffer.length - 2))))
        } else {
          None
        }
      } else {
        buffer.head match {
          case '+' => Some(RedisString(tail2str()))
          case '-' => Some(RedisError(tail2str()))
          case ':' => Some(RedisInteger(tail2str().toInt))
          case '$' =>
            bulkLen = Some(tail2str().toInt)
            buffer = Array[Byte]()
            None
          case '*' =>
            arrayLen = Some(tail2str().toInt)
            buffer = Array[Byte]()
            None
        }
      }

    (arrayLen, result) match {
      case (_, None) => None
      case (None, r) => buffer = Array[Byte]() ; r
      case (Some(len), Some(r)) =>
        buffer = Array[Byte]()
        arrays :+= r
        if (arrays.length == len) {
          Some(RedisArray(arrays))
        } else {
          None
        }
    }
  }

  override def send(byte: Byte): Try[Option[RedisData]] = {
    buffer :+= byte
    if (buffer.endsWith(Array('\r', '\n'))) {
      Try(tryDecode()) match {
        case Failure(e) => clear(); Failure(e)
        case Success(None) => Success(None)
        case Success(r) => clear(); Success(r)
      }
    } else {
      Success(None)
    }
  }

}
