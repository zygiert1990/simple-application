package com.zygiert

import sttp.tapir.server.netty.NettyFutureServer

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.io.StdIn

@main def run(): Unit =

  val host = sys.env.getOrElse("HTTP_HOST", "0.0.0.0")
  val port = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)
  val binding = Await.result(
    NettyFutureServer().host(host).port(port).addEndpoints(Endpoints.all).start(),
    Duration.Inf
  )
  println(s"Server started at http://$host:${binding.port}. Press ENTER key to exit.")
  // stdin is read on the main thread, a worker of the global ExecutionContext blocked on it
  // starves request handling when the JVM sees 1 CPU
  StdIn.readLine()
  Await.result(binding.stop(), Duration.Inf)
