package com.example.helloworld

import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.DispatcherSelector
import akka.grpc.{GrpcClientSettings, Trailers}
import akka.grpc.internal.{GrpcMetadataImpl, GrpcProtocolNative, GrpcRequestHelpers, Identity}
import akka.grpc.scaladsl.{Metadata, MetadataBuilder}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.{Chunked, LastChunk}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.google.protobuf.any.Any.fromJavaProto
import com.google.rpc.{Code, Status}
import com.typesafe.config.ConfigFactory
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

class ThirdRichErrorSpec extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {


  "The default ExceptionHandler" should {
    "return rich error" in {

      //val testKit = ActorTestKit()

      val conf = ConfigFactory
        .parseString("akka.http.server.preview.enable-http2 = on")
        .withFallback(ConfigFactory.defaultApplication())
      val system = ActorSystem("HelloWorld", conf)
      implicit val sys: ActorSystem = system
      implicit val ec: ExecutionContext = sys.dispatcher

      def customHandler(system: ActorSystem): PartialFunction[Throwable, Trailers] ={
        case grpcException: StatusRuntimeException =>
          Trailers(grpcException.getStatus, new GrpcMetadataImpl(grpcException.getTrailers))
      }

      val service: HttpRequest => Future[HttpResponse] =
        GreeterServiceHandler(new GreeterServiceImpl(system), eHandler = customHandler)

      val bound: Future[Http.ServerBinding] = Http(system)
        .newServerAt(interface = "127.0.0.1", port = 8080)
        .bind(service)

      bound.onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          println("gRPC server bound to {}:{}", address.getHostString, address.getPort)
        case Failure(ex) =>
          println("Failed to bind gRPC endpoint, terminating system", ex)
          system.terminate()
      }
      Thread.sleep(1000)
      try {

        val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8080).withTls(false)
        val client = GreeterServiceClient(clientSettings)

        println("Performing request")
        val finalReply = Await.result(client.sayHello(HelloRequest("Bob")).failed, Duration(1, TimeUnit.SECONDS))

        finalReply shouldBe an[StatusRuntimeException]

        val casted: StatusRuntimeException = finalReply.asInstanceOf[StatusRuntimeException]

        val status: com.google.rpc.Status = StatusProto.fromStatusAndTrailers(casted.getStatus, casted.getTrailers)

        import com.example.helloworld.HelloErrorReply.messageCompanion
        val customErrorReply = fromJavaProto(status.getDetails(0)).unpack
        customErrorReply shouldBe an[HelloErrorReply]

        status.getCode should be(3)
        status.getMessage should be("What is wrong?")
        customErrorReply.errorMessage should be("The password!")

      } finally {
        println("stopping")
        system.terminate()
        println("stopped")
      }

    }
  }
}
