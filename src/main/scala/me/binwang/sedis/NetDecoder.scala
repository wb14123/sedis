package me.binwang.sedis

import scala.util.{Failure, Success, Try}

trait NetDecoder[T] {
  def send(data: Byte): Try[Option[T]]

  def decode(data: Array[Byte]): Seq[Try[T]] = {
    data.flatMap { byte =>
      send(byte) match {
        case Success(None) => None
        case Failure(e) => Some(Failure(e))
        case Success(Some(r)) => Some(Success(r))
      }
    }
  }
}
