/*******************************************************************************
 * Copyright (c) 2026 Angelo Zerr and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from java-ls (https://github.com/tsmaeder/java-ls) and adapted.
 * Original code by Thomas Mäder, Castle Ridge Software, licensed under MIT.
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath;

/**
 * A single entry on a {@link ClasspathOrder}. Decides for itself whether
 * a given source URI (as stamped on
 * {@link org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry#sourceUri()}) is
 * "owned" by this entry.
 *
 * <p>Sealed so adding new entry shapes is an explicit decision; today
 * the only implementation is {@link UriClasspathEntry}.
 */
public sealed interface ClasspathEntry permits UriClasspathEntry {

    /** True if this entry owns {@code sourceUri}. */
    boolean contains(String sourceUri);
}
