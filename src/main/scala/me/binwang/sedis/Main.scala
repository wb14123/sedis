package me.binwang.sedis

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Main {

  def newClient() = new NetClient(100, "localhost", 6379, () => new RedisDecoder)
  val client = new NetClientPool(4, newClient)
  val decoder = new RedisDecoder

  def debugRedis(cmd: String): Future[Unit] = {
    client.send(RedisEncoder.encode(cmd.getBytes())).map { res =>
      println(res)
    }
  }

  def debugLoop(cmd: String): Future[Nothing] = {
    client.send(RedisEncoder.encode(cmd.getBytes())).flatMap { res =>
      debugLoop(cmd)
    }
  }

  def main(args: Array[String]): Unit = {
    for (_ <- 1 to 100000) {
      debugLoop("SET key 1")
    }
    Thread.sleep(10000)
  }

}
