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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.bloom;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.ResourceUris;

/**
 * One per-resource identifier bloom filter, addressed the same way as
 * type entries: classpath {@code sourceUri} plus compact
 * {@code resourcePath}.
 */
public record BloomEntry(String sourceUri, String resourcePath, IdentifierBloomFilter filter) {
    public String resourceUri() {
        return ResourceUris.resolve(sourceUri, resourcePath);
    }
}
