package com.zygiert

import org.crac.{Context, Core, Resource}
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.StdIn

@main def run(): Unit =

  val port = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)
  val serverManager = ServerManager(NettyFutureServer().port(port).addEndpoints(Endpoints.all).start())
  Core.getGlobalContext.register(serverManager)
  val program =
    for
      binding <- serverManager.binding
      _ <- Future:
        println(s"Server started at http://localhost:${binding.port}. Press ENTER key to exit.")
        StdIn.readLine()
      stop <- binding.stop()
    yield stop

  Await.result(program, Duration.Inf)

class ServerManager(val binding: Future[NettyFutureServerBinding]) extends Resource:

  override def beforeCheckpoint(context: Context[_ <: Resource]): Unit =
    println(s"checkpoint started")
    Await.result(binding.flatMap(_.stop()), Duration.Inf)
    println(s"Checkpoint created")
    ()

  override def afterRestore(context: Context[_ <: Resource]): Unit =
    println(s"we are inside restore")
    ()

object ServerManager:
  def apply(binding: Future[NettyFutureServerBinding]): ServerManager = new ServerManager(binding)
