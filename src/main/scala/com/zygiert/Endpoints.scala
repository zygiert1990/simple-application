package com.zygiert

import sttp.tapir.*

import scala.concurrent.Future
import sttp.tapir.server.ServerEndpoint

object Endpoints:
  case class User(name: String) extends AnyVal
  val helloEndpoint: PublicEndpoint[User, Unit, String, Any] = endpoint.get
    .in("hello")
    .in(query[User]("name"))
    .out(stringBody)
  val helloServerEndpoint: ServerEndpoint[Any, Future] = helloEndpoint.serverLogicSuccess(user => Future.successful(s"Hello ${user.name}"))

  val apiEndpoints: List[ServerEndpoint[Any, Future]] = List(helloServerEndpoint)

  val all: List[ServerEndpoint[Any, Future]] = apiEndpoints
