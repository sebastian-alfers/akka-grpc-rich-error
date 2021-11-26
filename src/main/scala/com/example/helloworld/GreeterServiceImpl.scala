package com.example.helloworld

//#import
import scala.concurrent.Future
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.MetadataBuilder
import akka.stream.scaladsl.BroadcastHub
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.MergeHub
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.protobuf.any.Any.{fromJavaProto, toJavaProto}
import com.google.protobuf.{Any, any}
import io.grpc.{Status, StatusException, StatusRuntimeException}
import com.google.rpc.{Code, Status => gStatus}
import io.grpc.protobuf.StatusProto

//#import

//#service-request-reply
//#service-stream
class GreeterServiceImpl(system: ActorSystem[_]) extends GreeterService {
  private implicit val sys: ActorSystem[_] = system

  //#service-request-reply
  val (inboundHub: Sink[HelloRequest, NotUsed], outboundHub: Source[HelloReply, NotUsed]) =
    MergeHub.source[HelloRequest]
    .map(request => HelloReply(s"Hello, ${request.name}"))
      .toMat(BroadcastHub.sink[HelloReply])(Keep.both)
      .run()
  //#service-request-reply

  override def sayHello(request: HelloRequest): Future[HelloReply] = {
    if(request.name == "Bob") {

      val status: gStatus = gStatus.newBuilder()
        .setCode(Code.INVALID_ARGUMENT.getNumber)
        .setMessage("What is wrong?")
        .addDetails(
          toJavaProto(
            com.google.protobuf.any.Any.pack(new HelloErrorReply(errorMessage = "The password!"))
          )
        )
        .build()

      val ex: StatusRuntimeException = StatusProto.toStatusRuntimeException(status)
      Future.failed(
        ex
      )
    } else {
      Future.successful(HelloReply(s"Hello, ${request.name}"))
    }
  }

  //#service-request-reply
  override def sayHelloToAll(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = {
    in.runWith(inboundHub)
    outboundHub
  }
  //#service-request-reply
}
//#service-stream
//#service-request-reply
