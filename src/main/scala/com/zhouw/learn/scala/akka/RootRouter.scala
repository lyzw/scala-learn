package com.zhouw.learn.scala.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.io.StdIn

object RootRouter {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route =
      pathPrefix("system") {
        //app开头的路径
        AppRouter.subPathPrefix("app") ~
          //h5开头的路径
          H5Router.subPathPrefix("h5")
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }


  trait SubRouter {
    def subPathPrefix(pathName: String): Route
  }

  object AppRouter extends SubRouter {
    override def subPathPrefix(pathName: String = "app"): Route = {
      pathPrefix(pathName) {
        get {
          path("test") {
            complete("appSubRequestRouter~")
          }
        }
      }
    }
  }

  object H5Router extends SubRouter {
    override def subPathPrefix(pathName: String = "h5"): Route = {
      pathPrefix(pathName) {
        path("test") {
          get {
            complete("H5SubRequestRouter~")
          }
        }
      }
    }
  }

}
