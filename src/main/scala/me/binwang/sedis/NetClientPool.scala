package me.binwang.sedis

import scala.concurrent.Future

class NetClientPool[DecodeT](poolSize: Int, netClientFactory: () => NetClient[DecodeT]) {

  val clients = (1 to poolSize).map(_ => netClientFactory())
  clients.foreach(_.start())
  var current = `0

  def send(data: Array[Byte]): Future[DecodeT] = {
    current = (current + 1) % poolSize
    clients(current).send(data)
  }
}
