## akka http输入输出流程初解

刚学习scala，项目中用到了akka-http作为restful接口的开发框架。想要了解一下akka-http的输入输出的流程，尝试看下akka-http的源码，如下是看源码的笔记。

### 版本信息
项目 | 版本
--- | ---
jdk | 1.8.0_171
scala | 2.12.6
akka-http | 10.0.11   
akka | 2.5.7

### 项目依赖
```
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-http_2.12</artifactId>
            <version>10.0.11</version>
        </dependency>
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
        <dependency>
            <groupId>com.typesafe.akka</groupId>
            <artifactId>akka-http-spray-json_2.12</artifactId>
            <version>10.1.1</version>
        </dependency>
```


### 测试代码
以官方网站中以JSON作为输入输出的的示例程序作为源码来分析

针对于http请求的输入与输出，关键性的代码有如下几个：

```
// 引入相关的代码，其中包含了很多的隐式函数，针对输入输出做转换
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

object WebServer {
    //省略部分代码
    implicit val itemFormat = jsonFormat2(Item)
    implicit val orderFormat = jsonFormat1(Order)
    val route: Route = {
        get {
        pathPrefix("item" / LongNumber) { id =>
          // there might be no item for a given id
          val maybeItem: Future[Option[Item]] = fetchItem(id)

          onSuccess(maybeItem) {
            case Some(item) => complete(item)
            case None => complete(StatusCodes.NotFound)
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
    //省略部分代码
}
```
本次我们分析akka如何处理输入输出，暂时不对akka-http如何接受请求和如何返回进行分析。

### 输入
输入的解析入口是entity(as[Order]),entity函数的源代码如下：
```scala
//MarshallingDirectives
  //entity接受一个FromRequestUnmarshaller[T]类型的入参
  def entity[T](um: FromRequestUnmarshaller[T]): Directive1[T] =
    extractRequestContext.flatMap[Tuple1[T]] { ctx ⇒
      import ctx.executionContext
      import ctx.materializer
      onComplete(um(ctx.request)) flatMap {
        case Success(value) ⇒ provide(value)
        .......//失败处理
      }
    } & cancelRejections(RequestEntityExpectedRejection.getClass, classOf[UnsupportedRequestContentTypeRejection])

```
从源码中我们可以看到entity函数接受一个FromRequestUnmarshaller[T]类型的入参，那么as[Order]应该返回的是FromRequestUnmarshaller[Order]类型的数据，我们再看看as函数：
```scala
def as[T](implicit um: FromRequestUnmarshaller[T]) = um

package object unmarshalling {
  //#unmarshaller-aliases
  type FromEntityUnmarshaller[T] = Unmarshaller[HttpEntity, T]
  type FromMessageUnmarshaller[T] = Unmarshaller[HttpMessage, T]
  type FromResponseUnmarshaller[T] = Unmarshaller[HttpResponse, T]
  type FromRequestUnmarshaller[T] = Unmarshaller[HttpRequest, T]
  type FromByteStringUnmarshaller[T] = Unmarshaller[ByteString, T]
  type FromStringUnmarshaller[T] = Unmarshaller[String, T]
  type FromStrictFormFieldUnmarshaller[T] = Unmarshaller[StrictForm.Field, T]
  //#unmarshaller-aliases
}
```
as接受了一个FromRequestUnmarshaller[T]类型的参数，FromRequestUnmarshaller[T]是type FromRequestUnmarshaller[T] = Unmarshaller[HttpRequest, T]的别名。

现在回去看看我们的请求参数的入口处理函数entity(as[Order])，我们给定的是一个Order类型，那么中间的参数是怎么转换，怎么变成一个FromRequestUnmarshaller入参的呢？

这就用到了scala中的一个很重要概念：隐式。
1、在前面，我们定义了两个隐式变量：
```scala
    implicit val itemFormat = jsonFormat2(Item)
    implicit val orderFormat = jsonFormat1(Order)
```
我们看看jsonFormat1的源码
```scala
  //定义JsonFormat的别名（简称）
  private[json] type JF[T] = JsonFormat[T] // simple alias for reduced verbosity
  //将
  def jsonFormat1[P1 :JF, T <: Product :ClassManifest](construct: (P1) => T): RootJsonFormat[T] = {
  //获取属性列表
    val Array(p1) = extractFieldNames(classManifest[T])
    jsonFormat(construct, p1)
  }
  def jsonFormat[P1 :JF, T <: Product](construct: (P1) => T, fieldName1: String): RootJsonFormat[T] = new RootJsonFormat[T]{
    def write(p: T) = {
      val fields = new collection.mutable.ListBuffer[(String, JsValue)]
      fields.sizeHint(1 * 2)
      fields ++= productElement2Field[P1](fieldName1, p, 0)
      JsObject(fields: _*)
    }
    def read(value: JsValue) = {
      val p1V = fromField[P1](value, fieldName1)
      construct(p1V)
    }
  }
```
jsonFormat1与jsonFormat2返回RootJsonFormat类型的数据，此时的数据类型还是不满足entity函数的要求，此时会调用另外一个导入的SprayJsonSupport类中隐式函数
```scala
trait SprayJsonSupport {
  implicit def sprayJsonUnmarshallerConverter[T](reader: RootJsonReader[T]): FromEntityUnmarshaller[T] =
    sprayJsonUnmarshaller(reader)

  implicit def sprayJsonUnmarshaller[T](implicit reader: RootJsonReader[T]): FromEntityUnmarshaller[T] =FromEntityUnmarshaller
  //将JsValue的数据转换为对应的类型，返回
    sprayJsValueUnmarshaller.map(jsonReader[T].read)
    
  implicit def sprayJsValueUnmarshaller: FromEntityUnmarshaller[JsValue] =
    //获取ByteString的请求内容
    Unmarshaller.byteStringUnmarshaller
    //只处理content-type为application/json的请求
      .forContentTypes(`application/json`)
      //将ByteString内容转换为JsValue
      .andThen(sprayJsValueByteStringUnmarshaller)
      
    implicit def sprayJsValueByteStringUnmarshaller[T]: FromByteStringUnmarshaller[JsValue] =
    Unmarshaller.withMaterializer[ByteString, JsValue](_ ⇒ implicit mat ⇒ { bs ⇒
      // .compact so addressing into any address is very fast (also for large chunks)
      // TODO we could optimise ByteStrings to better handle linear access like this (or provide ByteStrings.linearAccessOptimised)
      // TODO IF it's worth it.
      val parserInput = new SprayJsonByteStringParserInput(bs.compact)
      FastFuture.successful(JsonParser(parserInput))
    })
    //省略与本次输入无关的方法
}
trait PredefinedFromEntityUnmarshallers extends MultipartUnmarshallers {
  //获取请求里的数据
  implicit def byteStringUnmarshaller: FromEntityUnmarshaller[ByteString] =
    Unmarshaller.withMaterializer(_ ⇒ implicit mat ⇒ {
      case HttpEntity.Strict(_, data) ⇒ FastFuture.successful(data)
      case entity                     ⇒ entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
    })
}
```
经过这些步骤后我们拿到了FromEntityUnmarshaller[Order]类型的数据，但跟FromRequestUnmarshaller[Order]还是不一致。这会应该怎么弄呢？

经过使用debug模式，发现中间差了很重要的一环：
 ```scala
 sealed trait LowerPriorityGenericUnmarshallers{
   implicit def messageUnmarshallerFromEntityUnmarshaller[T](implicit um: FromEntityUnmarshaller[T]): FromMessageUnmarshaller[T] =
    Unmarshaller.withMaterializer { implicit ec ⇒ implicit mat ⇒ request ⇒ um(request.entity) }
}
 ```
 到此处，参数已经转变为FromMessageUnmarshaller[Order]，后续怎么转换成FromRequestUnmarshaller[Order]，暂时没有看出来，后续再研究吧//TODO
 
 ### 输出
 输入处理完成后，我们就得到了我们需要的参数，之后完成相应的业务逻辑得到对应的实体类结果，就可以将其转换为对应的输出。如上述例子中的get方法。
 
 输出可以看成是输入相反的过程，其主要的代码如下：
 ```scala
    implicit val itemFormat = jsonFormat2(Item)
    implicit val orderFormat = jsonFormat1(Order)
    onSuccess(maybeItem) {
        case Some(item) => complete(item)
        case None => complete(StatusCodes.NotFound)
    }
    
trait RouteDirectives {    
      def complete(m: ⇒ ToResponseMarshallable): StandardRoute =
    StandardRoute(_.complete(m))
}
 ```
 同样，输出有一系列的隐式函数对其进行支持，主要如下：
 ```scala
 trait SprayJsonSupport {
   //#sprayJsonMarshallerConverter
  implicit def sprayJsonMarshallerConverter[T](writer: RootJsonWriter[T])(implicit printer: JsonPrinter = CompactPrinter): ToEntityMarshaller[T] =
    sprayJsonMarshaller[T](writer, printer)
  //#sprayJsonMarshallerConverter
  implicit def sprayJsonMarshaller[T](implicit writer: RootJsonWriter[T], printer: JsonPrinter = CompactPrinter): ToEntityMarshaller[T] =
    sprayJsValueMarshaller compose writer.write
  implicit def sprayJsValueMarshaller(implicit printer: JsonPrinter = CompactPrinter): ToEntityMarshaller[JsValue] =
    Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)(printer)
}


trait ProductFormatsInstances { self: ProductFormats with StandardFormats =>

  def jsonFormat2[P1 :JF, P2 :JF, T <: Product :ClassManifest](construct: (P1, P2) => T): RootJsonFormat[T] = {
    val Array(p1, p2) = extractFieldNames(classManifest[T])
    jsonFormat(construct, p1, p2)
  }
  def jsonFormat[P1 :JF, P2 :JF, T <: Product](construct: (P1, P2) => T, fieldName1: String, fieldName2: String): RootJsonFormat[T] = new RootJsonFormat[T]{
    def write(p: T) = {
      val fields = new collection.mutable.ListBuffer[(String, JsValue)]
      fields.sizeHint(2 * 3)
      fields ++= productElement2Field[P1](fieldName1, p, 0)
      fields ++= productElement2Field[P2](fieldName2, p, 1)
      JsObject(fields: _*)
    }
    def read(value: JsValue) = {
      val p1V = fromField[P1](value, fieldName1)
      val p2V = fromField[P2](value, fieldName2)
      construct(p1V, p2V)
    }
  }
 ```
 
 输出转换
 ```scala
 trait LowPriorityToResponseMarshallerImplicits {
  implicit def liftMarshallerConversion[T](m: ToEntityMarshaller[T]): ToResponseMarshaller[T] =
    liftMarshaller(m)
  implicit def liftMarshaller[T](implicit m: ToEntityMarshaller[T]): ToResponseMarshaller[T] =
    PredefinedToResponseMarshallers.fromToEntityMarshaller()

  @deprecated("This method exists only for the purpose of binary compatibility, it used to be implicit.", "10.0.2")
  def fromEntityStreamingSupportAndEntityMarshaller[T, M](s: EntityStreamingSupport, m: ToEntityMarshaller[T]): ToResponseMarshaller[Source[T, M]] =
    fromEntityStreamingSupportAndEntityMarshaller(s, m, null)

  // FIXME deduplicate this!!!
  implicit def fromEntityStreamingSupportAndEntityMarshaller[T, M](implicit s: EntityStreamingSupport, m: ToEntityMarshaller[T], tag: ClassTag[T]): ToResponseMarshaller[Source[T, M]] = {
    Marshaller[Source[T, M], HttpResponse] { implicit ec ⇒ source ⇒
      FastFuture successful {
        Marshalling.WithFixedContentType(s.contentType, () ⇒ {
          val availableMarshallingsPerElement = source.mapAsync(1) { t ⇒ m(t)(ec) }

          // TODO optimise such that we pick the optimal marshalling only once (headAndTail needed?)
          // TODO, NOTE: this is somewhat duplicated from Marshal.scala it could be made DRYer
          val bestMarshallingPerElement = availableMarshallingsPerElement mapConcat { marshallings ⇒
            // pick the Marshalling that matches our EntityStreamingSupport
            val selectedMarshallings = (s.contentType match {
              case best @ (_: ContentType.Binary | _: ContentType.WithFixedCharset | _: ContentType.WithMissingCharset) ⇒
                marshallings collectFirst { case Marshalling.WithFixedContentType(`best`, marshal) ⇒ marshal }

              case best @ ContentType.WithCharset(bestMT, bestCS) ⇒
                marshallings collectFirst {
                  case Marshalling.WithFixedContentType(`best`, marshal) ⇒ marshal
                  case Marshalling.WithOpenCharset(`bestMT`, marshal)    ⇒ () ⇒ marshal(bestCS)
                }
            }).toList

            // TODO we could either special case for certrain known types,
            // or extend the entity support to be more advanced such that it would negotiate the element content type it
            // is able to render.
            if (selectedMarshallings.isEmpty) throw new NoStrictlyCompatibleElementMarshallingAvailableException[T](s.contentType, marshallings)
            else selectedMarshallings
          }

          val marshalledElements: Source[ByteString, M] =
            bestMarshallingPerElement.map(_.apply()) // marshal!
              .flatMapConcat(_.dataBytes) // extract raw dataBytes
              .via(s.framingRenderer)

          HttpResponse(entity = HttpEntity(s.contentType, marshalledElements))
        }) :: Nil
      }
    }
  }
}

 ```
 
 ### 总结
 
从如上的流程，我们可以看出来，scala的整体流程同样的遵循http协议，其处理步骤其实与java中的是相同的，只是在处理的过程中，大量的使用了scala隐式的特性，其中很多的转换动作都是隐蔽、不太容易发现的。隐式是个好东西，可以节省我们很多的代码量，但一旦没用好，可能会导致很多意想不到的问题，同时对初学者来说，大量的隐式，也会导致很多情况下摸不着头脑，学习成本大大提高。