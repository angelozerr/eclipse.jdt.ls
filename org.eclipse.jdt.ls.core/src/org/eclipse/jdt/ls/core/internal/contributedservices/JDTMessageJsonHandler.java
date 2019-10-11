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

import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;

import com.google.gson.GsonBuilder;

/**
 * @author azerr
 *
 */
public class JDTMessageJsonHandler extends MessageJsonHandler {

	public JDTMessageJsonHandler(Map<String, JsonRpcMethod> supportedMethods, Consumer<GsonBuilder> configureGson) {
		super(supportedMethods, configureGson);
	}

	public JDTMessageJsonHandler(Map<String, JsonRpcMethod> supportedMethods) {
		super(supportedMethods);
	}

	@Override
	public JsonRpcMethod getJsonRpcMethod(String name) {
		// call standards LSP services
		JsonRpcMethod method = super.getJsonRpcMethod(name);
		if (method == null) {
			// get external services from extension point
			return ContributedServicesExtensionPoint.getInstance().getJsonRpcMethod(name);
		}
		return method;
	}

}
