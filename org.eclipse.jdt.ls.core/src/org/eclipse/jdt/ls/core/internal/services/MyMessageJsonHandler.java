package org.eclipse.jdt.ls.core.internal.services;

import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;

import com.google.gson.GsonBuilder;

public class MyMessageJsonHandler extends MessageJsonHandler {

	public MyMessageJsonHandler(Map<String, JsonRpcMethod> supportedMethods, Consumer<GsonBuilder> configureGson) {
		super(supportedMethods, configureGson);
	}

	public MyMessageJsonHandler(Map<String, JsonRpcMethod> supportedMethods) {
		super(supportedMethods);
	}

	@Override
	public JsonRpcMethod getJsonRpcMethod(String name) {
		// call standards LSP services
		JsonRpcMethod method = super.getJsonRpcMethod(name);
		if (method == null) {
			// TODO: load external services from extension point
			return CustomServicesExtensionPoint.getInstance().getJsonRpcMethod(name);
		}
		return method;
	}

}
