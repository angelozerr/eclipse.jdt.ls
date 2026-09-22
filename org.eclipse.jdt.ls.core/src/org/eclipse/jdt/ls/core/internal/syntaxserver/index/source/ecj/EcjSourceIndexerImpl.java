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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.source.ecj;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;

/**
 * Package-private entry used by {@link EcjSourceIndexer}.
 * Implementation lives in {@link EcjSourceIndexerEngine}.
 */
final class EcjSourceIndexerImpl {
    private EcjSourceIndexerImpl() {}

    static void index(String resourcePath, String sourceUri, CharSequence content, Index into) {
        EcjSourceIndexerEngine.index(resourcePath, sourceUri, content, into);
    }
}
