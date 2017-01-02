package me.binwang.sedis

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Main {

  val client = new NetClient(1024, 1024, "localhost", 6379, () => new RedisDecoder)
  client.newConn()
  client.start()
  val decoder = new RedisDecoder

  def debugRedis(cmd: String): Future[Unit] = {
    client.send(RedisEncoder.encode(cmd.getBytes())).map { res =>
      println(res)
    }
  }

  def main(args: Array[String]): Unit = {
    debugRedis("PING")
    debugRedis("SET key 1")
    debugRedis("GET key")
    Thread.sleep(1000)
  }

}
