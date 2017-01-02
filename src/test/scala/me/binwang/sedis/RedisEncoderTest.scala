package me.binwang.sedis

import org.scalatest.{FlatSpec, Matchers}

class RedisEncoderTest extends FlatSpec with Matchers {
  it should "encode redis command" in {
    val cmd = "GET key".getBytes
    val encoded = new String(RedisEncoder.encode(cmd))
    encoded should equal("*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n")
  }
}
