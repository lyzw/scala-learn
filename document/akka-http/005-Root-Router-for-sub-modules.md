## akka配置根Router与子路由
> 2018年05月31日

akka-http创建Router使用path、pathPrefix来检测用户的请求路径，根据路径的不同来做不同的处理。

刚接触公司的scala，发现公司的项目用的是定义多个router，并使用~连接的方式对各个不同的Router进行拼接。按照java的开发习惯总是不太习惯，总感觉很多地方非常的别扭，而且每个Router都需要把contextPath重新再写一遍，感觉没有必要。

因此尝试的改变下代码，最终效果如下：
```scala
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
        path("test") {
          get {
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


```

如上，只定义一个路由，其他的路径的选择均使用object进行跳转，如此可以简化路由的配置，可将同一模块的请求定义到一个object中。

当然，凡是有利就有弊，如上的方法虽然代码更加简单、清晰，但对于请求路径或者路径中部存在变化情况的支持并不是很好，因此，大家可以结合自己的实际情况，选择不同的处理方式，或者综合使用，总之，没有绝对，只有相对，适合自己的才是最好的。
