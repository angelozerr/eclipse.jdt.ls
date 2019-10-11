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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethodProvider;
import org.eclipse.lsp4j.jsonrpc.services.GenericEndpoint;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;

/**
 * @author azerr
 *
 */
public class ContributedServicesRegistry extends GenericEndpoint {

	private final Map<String, JsonRpcMethod> supportedMethods;

	private final Map<String, ContributedServiceWrapper> wrappers;

	private boolean initialized;

	/**
	 *
	 */
	public ContributedServicesRegistry() {
		super(new ArrayList<>());
		this.supportedMethods = new HashMap<>();
		this.wrappers = new HashMap<>();
	}

	public JsonRpcMethod getJsonRpcMethod(String name) {
		initIfNeeded();
		JsonRpcMethod method = supportedMethods.get(name);
		if (method != null) {
			return method;
		}
		ContributedServiceWrapper wrapper = wrappers.get(name);
		if (wrapper != null) {
			Object localService = wrapper.getLocalService();
			registerService(localService);
		}
		return supportedMethods.get(name);
	}

	private synchronized void initIfNeeded() {
		if (initialized) {
			return;
		}
		try {
			init();
		} finally {
			initialized = true;
		}
	}

	/**
	 *
	 */
	protected void init() {

	}

	public void register(String name, ContributedServiceWrapper wrapper) {
		wrappers.put(name, wrapper);
	}

	public void registerService(Object localService) {
		if (localService instanceof JsonRpcMethodProvider) {
			JsonRpcMethodProvider rpcMethodProvider = (JsonRpcMethodProvider) localService;
			supportedMethods.putAll(rpcMethodProvider.supportedMethods());
		} else {
			supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(localService.getClass()));
		}
		recursiveFindRpcMethods(localService, new HashSet<>(), new HashSet<>());
	}
}
