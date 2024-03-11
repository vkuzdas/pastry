package proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.15.0)",
    comments = "Source: pastry.proto")
public final class PastryServiceGrpc {

  private PastryServiceGrpc() {}

  public static final String SERVICE_NAME = "PastryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<proto.Pastry.JoinRequest,
      proto.Pastry.Empty> getJoinMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Join",
      requestType = proto.Pastry.JoinRequest.class,
      responseType = proto.Pastry.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<proto.Pastry.JoinRequest,
      proto.Pastry.Empty> getJoinMethod() {
    io.grpc.MethodDescriptor<proto.Pastry.JoinRequest, proto.Pastry.Empty> getJoinMethod;
    if ((getJoinMethod = PastryServiceGrpc.getJoinMethod) == null) {
      synchronized (PastryServiceGrpc.class) {
        if ((getJoinMethod = PastryServiceGrpc.getJoinMethod) == null) {
          PastryServiceGrpc.getJoinMethod = getJoinMethod = 
              io.grpc.MethodDescriptor.<proto.Pastry.JoinRequest, proto.Pastry.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "PastryService", "Join"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  proto.Pastry.JoinRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  proto.Pastry.Empty.getDefaultInstance()))
                  .setSchemaDescriptor(new PastryServiceMethodDescriptorSupplier("Join"))
                  .build();
          }
        }
     }
     return getJoinMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PastryServiceStub newStub(io.grpc.Channel channel) {
    return new PastryServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PastryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new PastryServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PastryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new PastryServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class PastryServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void join(proto.Pastry.JoinRequest request,
        io.grpc.stub.StreamObserver<proto.Pastry.Empty> responseObserver) {
      asyncUnimplementedUnaryCall(getJoinMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getJoinMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                proto.Pastry.JoinRequest,
                proto.Pastry.Empty>(
                  this, METHODID_JOIN)))
          .build();
    }
  }

  /**
   */
  public static final class PastryServiceStub extends io.grpc.stub.AbstractStub<PastryServiceStub> {
    private PastryServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private PastryServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PastryServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new PastryServiceStub(channel, callOptions);
    }

    /**
     */
    public void join(proto.Pastry.JoinRequest request,
        io.grpc.stub.StreamObserver<proto.Pastry.Empty> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getJoinMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class PastryServiceBlockingStub extends io.grpc.stub.AbstractStub<PastryServiceBlockingStub> {
    private PastryServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private PastryServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PastryServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new PastryServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public proto.Pastry.Empty join(proto.Pastry.JoinRequest request) {
      return blockingUnaryCall(
          getChannel(), getJoinMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class PastryServiceFutureStub extends io.grpc.stub.AbstractStub<PastryServiceFutureStub> {
    private PastryServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private PastryServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PastryServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new PastryServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<proto.Pastry.Empty> join(
        proto.Pastry.JoinRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getJoinMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_JOIN = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final PastryServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(PastryServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_JOIN:
          serviceImpl.join((proto.Pastry.JoinRequest) request,
              (io.grpc.stub.StreamObserver<proto.Pastry.Empty>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class PastryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PastryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return proto.Pastry.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PastryService");
    }
  }

  private static final class PastryServiceFileDescriptorSupplier
      extends PastryServiceBaseDescriptorSupplier {
    PastryServiceFileDescriptorSupplier() {}
  }

  private static final class PastryServiceMethodDescriptorSupplier
      extends PastryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    PastryServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (PastryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PastryServiceFileDescriptorSupplier())
              .addMethod(getJoinMethod())
              .build();
        }
      }
    }
    return result;
  }
}
