//#full-example
package com.example.helloworld

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import com.example.helloworld.HelloErrorReply.messageCompanion
import com.google.protobuf.any.Any.fromJavaProto
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class RichErrorSpec extends AnyWordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  val testKit = ActorTestKit()

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), scaled(100.millis))

  implicit val system: ActorSystem[_] = testKit.system

  val service = new GreeterServiceImpl(system)

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "GreeterServiceImpl" should {
    "reply to single request" in {

      val reply = service.sayHello(HelloRequest("Bob"))
      val finalReply = reply.failed.futureValue

      finalReply shouldBe an[StatusRuntimeException]
      //finalReply shouldBe an[StatusException]
      val casted: StatusRuntimeException = finalReply.asInstanceOf[StatusRuntimeException]

      val status: com.google.rpc.Status = StatusProto.fromStatusAndTrailers(casted.getStatus, casted.getTrailers)

      val customErrorReply = fromJavaProto(status.getDetails(0)).unpack
      customErrorReply shouldBe an[HelloErrorReply]

      status.getCode should be(3)
      status.getMessage should be("What is wrong?")
      customErrorReply.errorMessage should be("The password!")


    }
  }
}
//#full-example
