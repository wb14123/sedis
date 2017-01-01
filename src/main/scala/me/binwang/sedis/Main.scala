package me.binwang.sedis

import scala.concurrent.ExecutionContext.Implicits.global

object Main {

  def main(args: Array[String]): Unit = {
    val client = new NetClient(1024, 1024, "localhost", 8080)
    client.newConn()
    client.start()
    client.send("GET 1\r\n".toCharArray.map(_.toByte)).map { res =>
      println(res)
      println(new String(res))
    }
    println("send 1")
    client.send("GET 2\r\n".toCharArray.map(_.toByte)).map { res =>
      println(res)
      println(new String(res))
    }
    println("send 2")
    Thread.sleep(1000)
  }

}
