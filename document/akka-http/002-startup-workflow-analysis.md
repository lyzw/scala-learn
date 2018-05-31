2018年05月24日16:04:50
### 环境信息 
名称 | 版本
--- | ---
jdk | 1.8
scala | 2.12.6
akka-http | 10.0.11
ide | idea 

> 官方文档地址：https://doc.akka.io/docs/akka-http/10.0.11/scala/http/introduction.html

### 步骤


#### 1、工程中加入akka-http包

```
        <!-- For Akka 2.4.x or 2.5.x -->
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-http_2.12</artifactId>
            <version>10.0.11</version>
        </dependency>
        <!-- Only when running against Akka 2.5 explicitly depend on akka-streams in same version as akka-actor -->
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-stream_2.12</artifactId>
            <version>2.5.7</version> <!-- Or whatever the latest version is -->
        </dependency>
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-actor_2.12</artifactId>
            <version>2.5.7</version>
        </dependency>
```
#### 实验代码
```
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import scala.io.StdIn

object AkkaServer {
  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route =
      path("hello") {
        get {
          println("ssssssss")
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      } 

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
```
启动程序，此时报错：
```
Exception in thread "main" java.lang.NoSuchMethodError: scala.Product.$init$(Lscala/Product;)V
	at akka.util.Timeout.<init>(Timeout.scala:13)
	at akka.actor.ActorSystem$Settings.<init>(ActorSystem.scala:327)
	at akka.actor.ActorSystemImpl.<init>(ActorSystem.scala:650)
	at akka.actor.ActorSystem$.apply(ActorSystem.scala:244)
	at akka.actor.ActorSystem$.apply(ActorSystem.scala:287)
	at akka.actor.ActorSystem$.apply(ActorSystem.scala:232)
	at AkkaServer$.main(AkkaServer.scala:11)
	at AkkaServer.main(AkkaServer.scala)
```
按照java的报错习惯，猜测有可能是包没有依赖进来获取包的版本不对。检查包的版本：
> scala-sdk：2.12.6

> org.scala.lang:scala-library:2.11.8

怎么scala-library的版本是2.11.8，检查pom文件，发现是以前在做slick的时候会依赖scala-library包，且版本就是这个，删除slick依赖重新下载依赖包，scala-library变成了2.12.4了，成功了。

### 测试
访问localhost:8080/hello，返回正常了。
