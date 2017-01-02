package me.binwang.sedis

import me.binwang.sedis.RedisProtocol._
import org.scalatest.{FlatSpec, Matchers}

class RedisDecoderTest extends FlatSpec with Matchers {
  it should "decode simple string" in {
    val decoder = new RedisDecoder()
    decoder.decode("+PONG\r\n".getBytes) should equal(RedisString("PONG"))
  }

  it should "decode errors" in {
    val decoder = new RedisDecoder()
    decoder.decode("-Error Message\r\n".getBytes) should equal(RedisError("Error Message"))
  }

  it should "decode integers" in {
    val decoder = new RedisDecoder
    decoder.decode(":100\r\n".getBytes) should equal(RedisInteger(100))
  }

}
