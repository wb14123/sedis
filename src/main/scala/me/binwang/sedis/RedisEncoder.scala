package me.binwang.sedis

object RedisEncoder {
  def encode(cmd: Array[Byte]): Array[Byte]= {
    var result = Array[Byte]()
    var buffer = Array[Byte]()
    var len = 0

    def appendBuffer() = {
      result ++= (s"$$${buffer.length}\r\n".getBytes() ++ buffer ++ "\r\n".getBytes)
      buffer = Array[Byte]()
      len += 1
    }

    cmd.foreach{
      case ' ' if buffer.isEmpty =>
      case ' ' => appendBuffer()
      case byte => buffer :+= byte
    }
    appendBuffer()
    s"*$len\r\n".getBytes() ++ result
  }
}
