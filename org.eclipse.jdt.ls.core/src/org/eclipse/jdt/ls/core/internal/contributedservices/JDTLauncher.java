/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contributedservices;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.Launcher.Builder;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;

/**
 * @author azerr
 *
 */
public class JDTLauncher {

	static class JDTBuilder<T> extends Builder<T> {

		@Override
		protected MessageJsonHandler createJsonHandler() {
			Map<String, JsonRpcMethod> supportedMethods = getSupportedMethods();
			if (configureGson != null) {
				return new JDTMessageJsonHandler(supportedMethods, configureGson);
			} else {
				return new JDTMessageJsonHandler(supportedMethods);
			}
		}
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
		return new JDTBuilder<T>().setLocalService(localService).setRemoteInterface(remoteInterface).setInput(in).setOutput(out).setExecutorService(executorService).wrapMessages(wrapper).create();
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

}
