package me.binwang.sedis

import java.net.{InetSocketAddress, StandardSocketOptions}
import java.nio.ByteBuffer
import java.nio.channels.{SelectionKey, Selector, SocketChannel}
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

class NetClient(val queueSize: Int, val channelSize: Int, val host: String, val port: Int)
    extends Thread {

  type Data = Array[Byte]

  val bufferSize = 1024
  val maxBatchSize = 1024

  private val sendQueue = new ConcurrentLinkedQueue[(Data, Promise[Data])]()
  private var promiseMap = Map[Int, mutable.Queue[Promise[Data]]]()
  private var readMap = Map[Int, Data]()
  private var writeMap = Map[Int, ByteBuffer]()
  private val freeConnQueue = new ConcurrentLinkedQueue[SocketChannel]()

  private val selector = Selector.open()

  override def run(): Unit = {
    while(true) {
      if (selector.select() != 0) {
        val keysIter = selector.selectedKeys().iterator()
        while(keysIter.hasNext) {
          val key = keysIter.next()
          if (key.isReadable) {
            val conn = key.channel().asInstanceOf[SocketChannel]
            readConn(conn)
            writeConn(conn)
          } else if (key.isWritable) {
            val conn = key.channel().asInstanceOf[SocketChannel]
            val buffer = writeMap.get(conn.hashCode())
            if (buffer.isEmpty) {
              throw new Exception("Error to get buffer to write")
            }
            conn.write(buffer.get)
            if (!buffer.get.hasRemaining) {
              buffer.get.clear()
              conn.register(selector, SelectionKey.OP_READ)
            }
            writeMap += (conn.hashCode() -> buffer.get)
          }
          keysIter.remove()
        }
      }
    }
  }

  def newConn(): Unit = {
    val conn = SocketChannel.open()
    conn.setOption(StandardSocketOptions.SO_KEEPALIVE, new java.lang.Boolean(true))
    conn.connect(new InetSocketAddress(host, port))
    conn.configureBlocking(false)
    conn.register(selector, SelectionKey.OP_READ)
    freeConnQueue.add(conn)
  }


  def send(data: Data): Future[Data] = {
    val promise = Promise[Data]()
    sendQueue.add((data, promise))
    val conn = freeConnQueue.poll()
    if (conn != null) {
      writeConn(conn)
    }
    promise.future
  }


  private def readConn(conn: SocketChannel): Unit = {
    val connId = conn.hashCode()
    var data = readMap.getOrElse(connId, Array[Byte]())
    val buffer = ByteBuffer.allocate(1024)
    var readSize = 1
    while(readSize != 0) {
      readSize = conn.read(buffer)
      for(i <- 0 until readSize) {
        val byte = buffer.get(i)
        data :+= byte
        // read a row
        if (data.length > 1 && data(data.length - 2) == '\r' && data(data.length - 1) == '\n') {
          val promises = promiseMap.get(connId)
          if (promises.isEmpty) {
            throw new Exception("Cannot find promise for data")
          }
          val promise = promises.get.dequeue()
          if (promise == null) {
            throw new Exception("Cannot find promise for data")
          }
          promise.success(data)
          data = Array[Byte]()
        }
      }
      buffer.clear()
    }
    readMap += (connId -> data)
  }


  private def writeConn(conn: SocketChannel): Unit = {
    var data = Array[Byte]()
    val promises = mutable.Queue[Promise[Data]]()
    var elem = sendQueue.poll()
    while (elem != null) {
      data ++= elem._1
      promises.enqueue(elem._2)
      elem = sendQueue.poll()
    }
    if (data.isEmpty) {
      freeConnQueue.add(conn)
    } else {
      promiseMap += (conn.hashCode() -> promises)
      val buffer = ByteBuffer.wrap(data)
      conn.write(buffer)
      /*
      Bytes in buffer are not all written into the connection, so may be the connection
      is not writable for now. Will write register it into the selector and write the remained
      buffer on the next write.
       */
      if (buffer.hasRemaining) {
        println("buffer is remaining")
        conn.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE)
        writeMap += (conn.hashCode() -> buffer)
      } else {
        buffer.clear()
      }
    }
  }
}
