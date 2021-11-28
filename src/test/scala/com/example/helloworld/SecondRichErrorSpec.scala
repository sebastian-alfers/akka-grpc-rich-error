package com.example.helloworld

import akka.actor.ActorSystem
import akka.grpc.Trailers
import akka.grpc.internal.{GrpcMetadataImpl, GrpcProtocolNative, GrpcRequestHelpers, Identity}
import akka.grpc.scaladsl.GrpcExceptionHandler
import akka.grpc.scaladsl.{Metadata, MetadataBuilder}
import akka.http.scaladsl.model.HttpEntity.{Chunked, LastChunk}
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import akka.util.ByteString
import com.example.helloworld.GreeterService.Serializers.HelloRequestSerializer
import com.google.protobuf.any.Any
import com.google.rpc.{Code, Status}
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class SecondRichErrorSpec
  extends TestKit(ActorSystem("GrpcExceptionHandlerSpec"))
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  def toJavaProto(scalaPbSource: com.google.protobuf.any.Any): com.google.protobuf.Any = {
    val javaPbOut = com.google.protobuf.Any.newBuilder
    javaPbOut.setTypeUrl(scalaPbSource.typeUrl)
    javaPbOut.setValue(scalaPbSource.value)
    javaPbOut.build
  }

  def fromJavaProto(javaPbSource: com.google.protobuf.Any): com.google.protobuf.any.Any =
    com.google.protobuf.any.Any(typeUrl = javaPbSource.getTypeUrl, value = javaPbSource.getValue)

  implicit val ec = system.dispatcher

  "The default ExceptionHandler" should {

    object RichErrorImpl extends GreeterService {


      import akka.NotUsed
      import akka.stream.scaladsl.Source


      def sayHello(in: HelloRequest): Future[HelloReply] = {
        println("****** is empty *********")
        val status: com.google.rpc.Status = com.google.rpc.Status
          .newBuilder()
          .setCode(Code.INVALID_ARGUMENT.getNumber)
          .setMessage("What is wrong?")
          .addDetails(toJavaProto(Any.pack(new HelloReply("The password!"))))
          .build()
        println("(((((((((")
        println(status)
        println("(((((((((")
        val ex = StatusProto.toStatusRuntimeException(status)
        println(ex)
        Future.failed(ex)
        //        Future.failed(
        //          new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription("No name found"), exceptionMetadata))
      }

      //#unary

      lazy val myResponseSource: Source[HelloReply, NotUsed] = ???

      def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = ???

      def itKeepsTalking(in: akka.stream.scaladsl.Source[HelloRequest, akka.NotUsed]): scala.concurrent.Future[HelloReply] = ???

      def streamHellos(in: akka.stream.scaladsl.Source[HelloRequest, akka.NotUsed])
      : akka.stream.scaladsl.Source[HelloReply, akka.NotUsed] = ???

      /**
       * #service-request-reply
       * #service-stream
       * The stream of incoming HelloRequest messages are
       * sent out as corresponding HelloReply. From
       * all clients to all clients, like a chat room.
       */
      override def sayHelloToAll(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = ???
    }

    "return rich error" in {

      implicit val serializer = HelloRequestSerializer
      implicit val writer = GrpcProtocolNative.newWriter(Identity)


      def customHandler(system: ActorSystem): PartialFunction[Throwable, Trailers] ={
        case grpcException: StatusRuntimeException =>
          Trailers(grpcException.getStatus, new GrpcMetadataImpl(grpcException.getTrailers))
      }

      val request =
        GrpcRequestHelpers(s"/${GreeterService.name}/SayHello", List.empty, Source.single(HelloRequest("")))
      val reply = Await.result(GreeterServiceHandler(RichErrorImpl, eHandler = customHandler).apply(request), Duration(1, TimeUnit.SECONDS))
      reply.status.intValue() should be(200)

      val lastChunk = reply.entity.asInstanceOf[Chunked].chunks.runWith(Sink.last).futureValue.asInstanceOf[LastChunk]
      val metadata: Metadata = MetadataBuilder.fromHeaders(lastChunk.trailer)

      val bs: ByteString = metadata.getBinary("grpc-status-details-bin").get

      val status: Status = com.google.rpc.Status.parseFrom(bs.toArray)

      status.getCode should be(Code.INVALID_ARGUMENT.getNumber)
      status.getMessage should be("What is wrong?")

      import HelloReply.messageCompanion
      val customErrorReply = fromJavaProto(status.getDetails(0)).unpack
      customErrorReply.message should be("The password!")


    }
  }
}
