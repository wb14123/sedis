package me.binwang.sedis

import RedisProtocol._

class RedisDecoder {
  def decode(data: Array[Byte]): RedisData = {

    def tail2str() = {
      new String(data.tail.take(data.length - 3))
    }

    data.head match {
      case '+' => RedisString(tail2str())
      case '-' => RedisError(tail2str())
      case ':' => RedisInteger(tail2str().toInt)
      case _ => throw new NotImplementedError()
    }
  }

}
