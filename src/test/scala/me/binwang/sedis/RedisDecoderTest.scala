package me.binwang.sedis

import me.binwang.sedis.RedisProtocol._
import org.scalatest.{FlatSpec, Matchers}

class RedisDecoderTest extends FlatSpec with Matchers {
  it should "decode simple string" in {
    val decoder = new RedisDecoder()
    decoder.decode("+PONG\r\n".getBytes).get should equal(RedisString("PONG"))
  }

  it should "decode errors" in {
    val decoder = new RedisDecoder()
    decoder.decode("-Error Message\r\n".getBytes).get should equal(RedisError("Error Message"))
  }

  it should "decode integers" in {
    val decoder = new RedisDecoder
    decoder.decode(":100\r\n".getBytes).get should equal(RedisInteger(100))
  }

  it should "decode string bulks" in {
    val decoder = new RedisDecoder
    decoder.decode("$3\r\nFOO\r\n".getBytes).get should equal(RedisString("FOO"))
  }

  it should "decode arrays" in {
    val decoder = new RedisDecoder
    decoder.decode(
      "*3\r\n$3\r\nFOO\r\n:100\r\n$3\r\nBAR\r\n".getBytes
    ).get should equal(
      RedisArray(Seq(RedisString("FOO"), RedisInteger(100), RedisString("BAR")))
    )
  }

}
