package org.eclipse.jdt.ls.core.internal.services;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.Launcher.Builder;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.validation.ReflectiveMessageValidator;

import com.google.gson.GsonBuilder;

public class MyBuilder<T> extends Builder<T> {

	/**
	 * Create a new Launcher for a given local service object, a given remote
	 * interface and an input and output stream.
	 *
	 * @param localService
	 *            - the object that receives method calls from the remote service
	 * @param remoteInterface
	 *            - an interface on which RPC methods are looked up
	 * @param in
	 *            - input stream to listen for incoming messages
	 * @param out
	 *            - output stream to send outgoing messages
	 */
	public static <T> Launcher<T> createLauncher(Object localService, Class<T> remoteInterface, InputStream in, OutputStream out) {
		return new MyBuilder<T>().setLocalService(localService).setRemoteInterface(remoteInterface).setInput(in).setOutput(out).create();
	}

	/**
	 * Create a new Launcher for a given local service object, a given remote
	 * interface and an input and output stream, and set up message validation and
	 * tracing.
	 *
	 * @param localService
	 *            - the object that receives method calls from the remote service
	 * @param remoteInterface
	 *            - an interface on which RPC methods are looked up
	 * @param in
	 *            - input stream to listen for incoming messages
	 * @param out
	 *            - output stream to send outgoing messages
	 * @param validate
	 *            - whether messages should be validated with the
	 *            {@link ReflectiveMessageValidator}
	 * @param trace
	 *            - a writer to which incoming and outgoing messages are traced, or
	 *            {@code null} to disable tracing
	 */
	public static <T> Launcher<T> createLauncher(Object localService, Class<T> remoteInterface, InputStream in, OutputStream out, boolean validate, PrintWriter trace) {
		return new MyBuilder<T>().setLocalService(localService).setRemoteInterface(remoteInterface).setInput(in).setOutput(out).validateMessages(validate).traceMessages(trace).create();
	}

	/**
	 * Create a new Launcher for a given local service object, a given remote
	 * interface and an input and output stream. Threads are started with the given
	 * executor service. The wrapper function is applied to the incoming and
	 * outgoing message streams so additional message handling such as validation
	 * and tracing can be included.
	 *
	 * @param localService
	 *            - the object that receives method calls from the remote service
	 * @param remoteInterface
	 *            - an interface on which RPC methods are looked up
	 * @param in
	 *            - input stream to listen for incoming messages
	 * @param out
	 *            - output stream to send outgoing messages
	 * @param executorService
	 *            - the executor service used to start threads
	 * @param wrapper
	 *            - a function for plugging in additional message consumers
	 */
	public static <T> Launcher<T> createLauncher(Object localService, Class<T> remoteInterface, InputStream in, OutputStream out, ExecutorService executorService, Function<MessageConsumer, MessageConsumer> wrapper) {
		return createIoLauncher(localService, remoteInterface, in, out, executorService, wrapper);
	}

	/**
	 * Create a new Launcher for a given local service object, a given remote
	 * interface and an input and output stream. Threads are started with the given
	 * executor service. The wrapper function is applied to the incoming and
	 * outgoing message streams so additional message handling such as validation
	 * and tracing can be included.
	 *
	 * @param localService
	 *            - the object that receives method calls from the remote service
	 * @param remoteInterface
	 *            - an interface on which RPC methods are looked up
	 * @param in
	 *            - input stream to listen for incoming messages
	 * @param out
	 *            - output stream to send outgoing messages
	 * @param executorService
	 *            - the executor service used to start threads
	 * @param wrapper
	 *            - a function for plugging in additional message consumers
	 */
	public static <T> Launcher<T> createIoLauncher(Object localService, Class<T> remoteInterface, InputStream in, OutputStream out, ExecutorService executorService, Function<MessageConsumer, MessageConsumer> wrapper) {
		return new MyBuilder<T>().setLocalService(localService).setRemoteInterface(remoteInterface).setInput(in).setOutput(out).setExecutorService(executorService).wrapMessages(wrapper).create();
	}

	/**
	 * Create a new Launcher for a given local service object, a given remote
	 * interface and an input and output stream. Threads are started with the given
	 * executor service. The wrapper function is applied to the incoming and
	 * outgoing message streams so additional message handling such as validation
	 * and tracing can be included. The {@code configureGson} function can be used
	 * to register additional type adapters in the {@link GsonBuilder} in order to
	 * support protocol classes that cannot be handled by Gson's reflective
	 * capabilities.
	 *
	 * @param localService
	 *            - the object that receives method calls from the remote service
	 * @param remoteInterface
	 *            - an interface on which RPC methods are looked up
	 * @param in
	 *            - input stream to listen for incoming messages
	 * @param out
	 *            - output stream to send outgoing messages
	 * @param executorService
	 *            - the executor service used to start threads
	 * @param wrapper
	 *            - a function for plugging in additional message consumers
	 * @param configureGson
	 *            - a function for Gson configuration
	 */
	public static <T> Launcher<T> createIoLauncher(Object localService, Class<T> remoteInterface, InputStream in, OutputStream out, ExecutorService executorService, Function<MessageConsumer, MessageConsumer> wrapper,
			Consumer<GsonBuilder> configureGson) {
		return new MyBuilder<T>().setLocalService(localService).setRemoteInterface(remoteInterface).setInput(in).setOutput(out).setExecutorService(executorService).wrapMessages(wrapper).configureGson(configureGson).create();
	}

	/**
	 * Create a new Launcher for a given local service object, a given remote
	 * interface and an input and output stream. Threads are started with the given
	 * executor service. The wrapper function is applied to the incoming and
	 * outgoing message streams so additional message handling such as validation
	 * and tracing can be included. The {@code configureGson} function can be used
	 * to register additional type adapters in the {@link GsonBuilder} in order to
	 * support protocol classes that cannot be handled by Gson's reflective
	 * capabilities.
	 *
	 * @param localService
	 *            - the object that receives method calls from the remote service
	 * @param remoteInterface
	 *            - an interface on which RPC methods are looked up
	 * @param in
	 *            - input stream to listen for incoming messages
	 * @param out
	 *            - output stream to send outgoing messages
	 * @param validate
	 *            - whether messages should be validated with the
	 *            {@link ReflectiveMessageValidator}
	 * @param executorService
	 *            - the executor service used to start threads
	 * @param wrapper
	 *            - a function for plugging in additional message consumers
	 * @param configureGson
	 *            - a function for Gson configuration
	 */
	public static <T> Launcher<T> createIoLauncher(Object localService, Class<T> remoteInterface, InputStream in, OutputStream out, boolean validate, ExecutorService executorService, Function<MessageConsumer, MessageConsumer> wrapper,
			Consumer<GsonBuilder> configureGson) {
		return new MyBuilder<T>().setLocalService(localService).setRemoteInterface(remoteInterface).setInput(in).setOutput(out).validateMessages(validate).setExecutorService(executorService).wrapMessages(wrapper)
				.configureGson(configureGson).create();
	}

	/**
	 * Create a new Launcher for a collection of local service objects, a collection
	 * of remote interfaces and an input and output stream. Threads are started with
	 * the given executor service. The wrapper function is applied to the incoming
	 * and outgoing message streams so additional message handling such as
	 * validation and tracing can be included. The {@code configureGson} function
	 * can be used to register additional type adapters in the {@link GsonBuilder}
	 * in order to support protocol classes that cannot be handled by Gson's
	 * reflective capabilities.
	 *
	 * @param localServices
	 *            - the objects that receive method calls from the remote services
	 * @param remoteInterfaces
	 *            - interfaces on which RPC methods are looked up
	 * @param classLoader
	 *            - a class loader that is able to resolve all given interfaces
	 * @param in
	 *            - input stream to listen for incoming messages
	 * @param out
	 *            - output stream to send outgoing messages
	 * @param executorService
	 *            - the executor service used to start threads
	 * @param wrapper
	 *            - a function for plugging in additional message consumers
	 * @param configureGson
	 *            - a function for Gson configuration
	 */
	static Launcher<Object> createIoLauncher(Collection<Object> localServices, Collection<Class<?>> remoteInterfaces, ClassLoader classLoader, InputStream in, OutputStream out, ExecutorService executorService,
			Function<MessageConsumer, MessageConsumer> wrapper, Consumer<GsonBuilder> configureGson) {
		return new MyBuilder<>().setLocalServices(localServices).setRemoteInterfaces(remoteInterfaces).setClassLoader(classLoader).setInput(in).setOutput(out).setExecutorService(executorService).wrapMessages(wrapper)
				.configureGson(configureGson).create();
	}

	@Override
	protected MessageJsonHandler createJsonHandler() {
		Map<String, JsonRpcMethod> supportedMethods = getSupportedMethods();
		if (configureGson != null) {
			return new MyMessageJsonHandler(supportedMethods, configureGson);
		} else {
			return new MyMessageJsonHandler(supportedMethods);
		}
	}
}
