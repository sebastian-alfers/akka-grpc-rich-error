package com.example.helloworld

//#import
import akka.NotUsed
import akka.actor.ActorSystem
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
class GreeterServiceImpl(system: ActorSystem) extends GreeterService {
  private implicit val sys: ActorSystem = system

  //#service-request-reply
  val (inboundHub: Sink[HelloRequest, NotUsed], outboundHub: Source[HelloReply, NotUsed]) =
    MergeHub.source[HelloRequest]
    .map(request => HelloReply(s"Hello, ${request.name}"))
      .toMat(BroadcastHub.sink[HelloReply])(Keep.both)
      .run()
  //#service-request-reply

  override def sayHello(request: HelloRequest): Future[HelloReply] = {
    if(request.name == "Bob") {
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
}
//#service-stream
//#service-request-reply
