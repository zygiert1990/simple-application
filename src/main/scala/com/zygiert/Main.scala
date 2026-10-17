package com.zygiert

import org.crac.{Context, Core, Resource}
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.io.StdIn

@main def run(): Unit =

  val serverManager = ServerManager()
  Core.getGlobalContext.register(serverManager)
  serverManager.start()
  // stdin is read on the main thread, a worker of the global ExecutionContext blocked on it
  // starves request handling when the JVM sees 1 CPU
  StdIn.readLine()
  serverManager.stop()

class ServerManager extends Resource:

  @volatile private var binding: Option[NettyFutureServerBinding] = None

  def start(): Unit =
    val host = sys.env.getOrElse("HTTP_HOST", "0.0.0.0")
    val port = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)
    val started = Await.result(
      NettyFutureServer().host(host).port(port).addEndpoints(Endpoints.all).start(),
      Duration.Inf
    )
    binding = Some(started)
    println(s"Server started at http://$host:${started.port}. Press ENTER key to exit.")

  def stop(): Unit =
    binding.foreach(b => Await.result(b.stop(), Duration.Inf))
    binding = None

  override def beforeCheckpoint(context: Context[? <: Resource]): Unit =
    println(s"checkpoint started")
    stop()
    println(s"Checkpoint created")

  // returns right after server start, thread which performed checkpoint must not be blocked
  override def afterRestore(context: Context[? <: Resource]): Unit =
    println(s"restore started")
    start()

object ServerManager:
  def apply(): ServerManager = new ServerManager()
