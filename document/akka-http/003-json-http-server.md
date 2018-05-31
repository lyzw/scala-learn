## akka-http学习-初体验（第二天）-1-输出为JSON格式

按照官网的文档编写了如下文档
```
package com.zhouw.learn.scala.test.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.Done
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

import scala.io.StdIn

import scala.concurrent.Future


object AkkaJSONRespServer {
  // needed to run the route
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext = system.dispatcher

  var orders: List[Item] = Nil

  // domain model
  final case class Item(name: String, id: Long)
  final case class Order(items: List[Item])

  // (fake) async database query api
  def fetchItem(itemId: Long): Future[Option[Item]] = Future {
    orders.find(o => o.id == itemId)
  }
  def saveOrder(order: Order): Future[Done] = {
    orders = order match {
      case Order(items) => items ::: orders
      case _            => orders
    }
    Future { Done }
  }

  def main(args: Array[String]): Unit = {
    // formats for unmarshalling and marshalling
    //使用隐式转换，将item的输出转换为JSON字符串
    implicit val itemFormat = jsonFormat2(Item)
    //使用隐式转换，将order的json字符串转换为相对应的对象
    implicit val orderFormat = jsonFormat1(Order)
    val route: Route =
      get {
        pathPrefix("item" / LongNumber) { id =>
          // there might be no item for a given id
          val maybeItem: Future[Option[Item]] = fetchItem(id)

          onSuccess(maybeItem) {
            case Some(item) => complete(item)
            case None       => complete(StatusCodes.NotFound)
          }
        }
      } ~
        post {
          path("create-order") {
            entity(as[Order]) { order =>
              val saved: Future[Done] = saveOrder(order)
              onComplete(saved) { done =>
                complete("order created")
              }
            }
          }
        }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ system.terminate()) // and shutdown when done

  }
}

```

此时会发现complete(item)与import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._会报错，说明少包了。

使用http://www.findmaven.net/jsp/index_cn.jsp查询少的类：akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport，发现应该是akka-http-spray-json包，尝试引入依赖，新增如下依赖
```

        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-http-spray-json_2.12</artifactId>
            <version>10.1.1</version>
        </dependency>
```
> PS：注意akka后面的_2.12是scala的版本依赖信息，最好保持所有的akka的版本一直，防止出现类或者方法找不到的异常。

完成后再次执行，发现可以运行成功。

> [POST] localhost:8080/create-order
```
{
    "items": [
        {
            "id": 1,
            "name": "Smith"
        },
        {
            "id": 2,
            "name": "Tom"
        }
    ]
}
```
创建成功

执行查询
> [GET]localhost:8080/item/1

```
{
    "name": "Smith",
    "id": 1
}
```

## 分析
整个JSON输入输出的核心就在于下面的几个代码：
```
//引入SprayJsonSupport中的隐式函数，
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

//使用隐式转换，将item的输出转换为JSON字符串
    implicit val itemFormat = jsonFormat2(Item)
    //使用隐式转换，将order的json字符串转换为相对应的对象
    implicit val orderFormat = jsonFormat1(Order)
```
使用隐式转换，将输入从json转为实体类，将输出从实体类转换为json从而实现了json输入输出的功能。

我们先看complete函数：
```
  def complete(m: ⇒ ToResponseMarshallable): StandardRoute =
    StandardRoute(_.complete(m))
```
complete函数要求一个返回类型为ToResponseMarshallable函数，在上述的例子中，我们给的是Item类型的值，那么这时候就会用到我们定义的隐式转换了。

系统在调用complete方法的时候，发现给的值是Item类型，此时他就会去找返回值是ToResponseMarshallable的方法，