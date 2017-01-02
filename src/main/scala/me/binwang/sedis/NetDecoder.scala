package me.binwang.sedis

import scala.util.{Failure, Success, Try}

trait NetDecoder[T] {
  def send(data: Byte): Try[Option[T]]

  def decode(data: Array[Byte]): Try[T] = {
    var result: Try[T] = Failure(new Exception("no enough data to decode"))
    data.foreach{byte =>
      send(byte) match {
        case Success(None) =>
        case Failure(e) => result = Failure(e)
        case Success(Some(r)) => result = Success(r)
      }
    }
    result
  }
}
