package me.binwang.sedis

object RedisProtocol {
  class RedisData
  case class RedisString(value: String) extends RedisData
  case class RedisError(msg: String) extends RedisData
  case class RedisInteger(value: Int) extends RedisData
  case class RedisArray(value: Seq[RedisData]) extends RedisData
}
