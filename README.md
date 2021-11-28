# Try akka-grpc with google Rich Error model

> :warning: WIP

Added dependency:

```
"io.grpc" % "grpc-protobuf" % "1.42.1"
```

# First example

Call the service directly.

## Server

1) Build the `Status` response [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/main/scala/com/example/helloworld/GreeterServiceImpl.scala#L34-L42)
2) set a `code` and `message` as with the [traditional error model](https://doc.akka.io/docs/akka-grpc/current/server/details.html#status-codes)
3) use `addDetails` to place a `com.google.protobuf.Any`
   1) Use a combination of `Any.pack` and `toJavaProto` to convert a `scalapb.GeneratedMessage` based class into a `com.google.protobuf.Any`
   2) This way you can attach anything to the error response.
4) Use `Future.failed` + `StatusProto.toStatusRuntimeException` to return the `Status` as a `StatusRuntimeException`

## Client
1) Expect a failed future as seen [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/test/scala/com/example/helloworld/RichErrorSpec.scala#L42)
2) Cast with `.asInstanceOf[StatusRuntimeException]`
3) Extract the "Rich Error model" (`com.google.rpc.Status`) by using `StatusProto.fromStatusAndTrailers(...)`
   1) Use a combination of `fromJavaProto` and `unpack`
   2) Make sure to place the implicit message companion (here `import com.example.helloworld.HelloErrorReply.messageCompanion`) to get the type you expect

Full service example can be found [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/main/scala/com/example/helloworld/GreeterServiceImpl.scala).
Full client example (spec) can be found [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/test/scala/com/example/helloworld/RichErrorSpec.scala).

# Second example

The service part is 

- Route the request through the `GreeterServiceHandler`
- Added a custom [case to the error handler](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/test/scala/com/example/helloworld/SecondRichErrorSpec.scala#L57-L60)
- Access the "Rich Error Model" directly through the `Metadata` [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/test/scala/com/example/helloworld/SecondRichErrorSpec.scala#L70)
- Import the message companion of the expected item stored in the details section [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/test/scala/com/example/helloworld/SecondRichErrorSpec.scala#L78-L79)

## More resources:
Links:
 - https://grpc.io/docs/guides/error/#richer-error-model
 - https://cloud.google.com/apis/design/errors#error_model