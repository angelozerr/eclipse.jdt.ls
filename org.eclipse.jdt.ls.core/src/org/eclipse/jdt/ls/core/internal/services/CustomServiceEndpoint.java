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
package org.eclipse.jdt.ls.core.internal.services;

import java.util.ArrayList;
import java.util.HashSet;

import org.eclipse.lsp4j.jsonrpc.services.GenericEndpoint;

/**
 * @author azerr
 *
 */
public class CustomServiceEndpoint extends GenericEndpoint {

	/**
	 * @param delegates
	 */
	public CustomServiceEndpoint() {
		super(new ArrayList<>());
	}

	public void addDelegate(Object delegate) {
		recursiveFindRpcMethods(delegate, new HashSet<>(), new HashSet<>());
	}


}
