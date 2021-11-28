package com.example.helloworld

//#import
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import com.google.protobuf.any.Any
import com.google.protobuf.any.Any.toJavaProto
import com.google.rpc.{Code, Status}
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto

import scala.concurrent.Future

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
      println("this is bob")
      // https://cloud.google.com/apis/design/errors#error_model
      val status: Status = Status.newBuilder()
        .setCode(Code.INVALID_ARGUMENT.getNumber)
        .setMessage("What is wrong?")
        .addDetails(
          toJavaProto(
            Any.pack(new HelloErrorReply(errorMessage = "The password!"))
          )
        )
        .build()

      Future.failed(StatusProto.toStatusRuntimeException(status))
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
