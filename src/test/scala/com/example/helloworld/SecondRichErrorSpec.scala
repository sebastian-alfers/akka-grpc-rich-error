package com.example.helloworld

import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.grpc.Trailers
import akka.grpc.internal.{GrpcMetadataImpl, GrpcProtocolNative, GrpcRequestHelpers, Identity}
import akka.grpc.scaladsl.GrpcExceptionHandler
import akka.grpc.scaladsl.{Metadata, MetadataBuilder}
import akka.http.scaladsl.model.HttpEntity.{Chunked, LastChunk}
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.example.helloworld.GreeterService.Serializers.HelloRequestSerializer

import com.google.protobuf.any.Any
import com.google.rpc.{Code, Status}
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AnyWordSpecLike}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class SecondRichErrorSpec extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  val testKit = ActorTestKit()
  implicit val system = testKit.system


  "The default ExceptionHandler" should {

    "return rich error" in {

      implicit val serializer = HelloRequestSerializer
      implicit val writer = GrpcProtocolNative.newWriter(Identity)

      val service = new GreeterServiceImpl(system.classicSystem)

      def customHandler(system: ActorSystem): PartialFunction[Throwable, Trailers] ={
        case grpcException: StatusRuntimeException =>
          Trailers(grpcException.getStatus, new GrpcMetadataImpl(grpcException.getTrailers))
      }

      val request =
        GrpcRequestHelpers(s"/${GreeterService.name}/SayHello", List.empty, Source.single(HelloRequest("Bob")))
      val reply = Await.result(GreeterServiceHandler(service, eHandler = customHandler).apply(request), Duration(1, TimeUnit.SECONDS))
      reply.status.intValue() should be(200)

      val lastChunk = reply.entity.asInstanceOf[Chunked].chunks.runWith(Sink.last).futureValue.asInstanceOf[LastChunk]
      val metadata: Metadata = MetadataBuilder.fromHeaders(lastChunk.trailer)

      val bs: ByteString = metadata.getBinary("grpc-status-details-bin").get

      val status: Status = com.google.rpc.Status.parseFrom(bs.toArray)
      println(status)

      status.getCode should be(Code.INVALID_ARGUMENT.getNumber)
      status.getMessage should be("What is wrong?")

      import com.google.protobuf.any.Any.fromJavaProto
      import HelloErrorReply.messageCompanion
      val customErrorReply = fromJavaProto(status.getDetails(0)).unpack
      customErrorReply.errorMessage should be("The password!")


    }
  }
}
