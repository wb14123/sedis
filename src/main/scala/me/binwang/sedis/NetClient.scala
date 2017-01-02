package me.binwang.sedis

import java.net.{InetSocketAddress, StandardSocketOptions}
import java.nio.ByteBuffer
import java.nio.channels.{SelectionKey, Selector, SocketChannel}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}


class NetClient[DecodeT](val channelSize: Int, val host: String,
    val port: Int, decoderFactory: () => NetDecoder[DecodeT]) extends Thread {

  type Data = Array[Byte]

  val bufferSize = 1024
  val maxBatchSize = 1024

  private val sendQueue = new ConcurrentLinkedQueue[(Data, Promise[DecodeT])]()
  private val freeConnQueue = new ConcurrentLinkedQueue[SocketChannel]()
  private val selector = Selector.open()
  private val channels = (1 to channelSize).map { _ => newConn()}
  private val promiseMap = channels.map(conn =>
    conn.hashCode() -> mutable.Queue[Promise[DecodeT]]()).toMap
  private val readMap = channels.map(conn =>
    conn.hashCode() -> decoderFactory()).toMap
  private val writeMap = new ConcurrentHashMap[Int, ByteBuffer]()

  override def run(): Unit = {
    while(true) {
      if (selector.select() != 0) {
        val keysIter = selector.selectedKeys().iterator()
        while(keysIter.hasNext) {
          val key = keysIter.next()
          if (key.isReadable) {
            val conn = key.channel().asInstanceOf[SocketChannel]
            readConn(conn)
            if (promiseMap(conn.hashCode()).isEmpty) {
              writeConn(conn)
            }
          } else if (key.isWritable) {
            println("writable")
            val conn = key.channel().asInstanceOf[SocketChannel]
            if (!writeMap.contains(conn.hashCode())) {
              throw new Exception("Error to get buffer to write")
            }
            val buffer = writeMap.get(conn.hashCode())
            conn.write(buffer)
            if (!buffer.hasRemaining) {
              buffer.clear()
              conn.register(selector, SelectionKey.OP_READ)
            }
            writeMap.put(conn.hashCode(), buffer)
          }
          keysIter.remove()
        }
      }
    }
  }

  private def newConn(): SocketChannel = {
    val conn = SocketChannel.open()
    conn.setOption(StandardSocketOptions.SO_KEEPALIVE, new java.lang.Boolean(true))
    conn.connect(new InetSocketAddress(host, port))
    conn.configureBlocking(false)
    conn.register(selector, SelectionKey.OP_READ)
    freeConnQueue.add(conn)
    conn
  }


  def send(data: Data): Future[DecodeT] = {
    val promise = Promise[DecodeT]()
    sendQueue.add((data, promise))
    val conn = freeConnQueue.poll()
    if (conn != null) {
      /*
       This operation will run on the thread which invoke this method.
       But this doesn't block and will only encode very few messages.
       So that should be OK.
      */
      writeConn(conn)
    }
    promise.future
  }


  private def readConn(conn: SocketChannel): Unit = {
    val connId = conn.hashCode()
    val decoder = readMap(connId)
    val buffer = ByteBuffer.allocate(bufferSize)
    var readSize = 1
    val promises = promiseMap(connId)
    while(readSize != 0) {
      readSize = conn.read(buffer)
      for(i <- 0 until readSize) {
        decoder.send(buffer.get(i)) match {
          case Failure(e) =>
            promises.dequeue().failure(e)
          case Success(None) =>
          case Success(Some(result)) =>
            promises.dequeue().success(result)
        }
      }
      buffer.clear()
    }
  }


  /*
  This method is invoked both by the selector thread and user thread,
  so the operations on this should be thread safe on each conn.
   */
  private def writeConn(conn: SocketChannel): Unit = {
    var data = Array[Byte]()
    val promises = promiseMap(conn.hashCode())
    var elem = sendQueue.poll()
    var alreadySize = 0
    while (elem != null && alreadySize < maxBatchSize) {
      data ++= elem._1
      promises.enqueue(elem._2)
      elem = sendQueue.poll()
      alreadySize += 1
    }
    if (data.isEmpty) {
      freeConnQueue.add(conn)
    } else {
      val buffer = ByteBuffer.wrap(data)
      conn.write(buffer)
      /*
      Bytes in buffer are not all written into the connection, so may be the connection
      is not writable for now. Register it into the selector and write the remained
      buffer on the next writable time.
       */
      if (buffer.hasRemaining) {
        conn.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE)
        writeMap.put(conn.hashCode(), buffer)
      } else {
        buffer.clear()
      }
    }
  }
}
