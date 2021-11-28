# Try akka-grpc with google Rich Error model

> :warning: WIP

Added dependency:

```
"io.grpc" % "grpc-protobuf" % "1.42.1"
```

Look at `ThirdRichErrorSpec`.

- The server returns a "Rich Error" [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/main/scala/com/example/helloworld/GreeterServiceImpl.scala#L33-L42) and returns it as a `StatusRuntimeException`
- Define and register [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/test/scala/com/example/helloworld/ThirdRichErrorSpec.scala#L47-L50) a custom error handler case to translate the `StatusRuntimeException` into a `Trailers`
- Cast the failed future exception back into a `StatusRuntimeException` [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/test/scala/com/example/helloworld/ThirdRichErrorSpec.scala#L74-L78)
- Retrieve the "Rich Error" form the Trailers [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/test/scala/com/example/helloworld/ThirdRichErrorSpec.scala#L80)
- Retrieve the `HelloErrorReply` stored in `details` [here](https://github.com/sebastian-alfers/akka-grpc-rich-error/blob/master/src/test/scala/com/example/helloworld/ThirdRichErrorSpec.scala#L82-L84)