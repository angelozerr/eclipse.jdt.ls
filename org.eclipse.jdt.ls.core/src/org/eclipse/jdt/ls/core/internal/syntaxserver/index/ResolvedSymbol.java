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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index;

import java.util.Optional;

import org.eclipse.lsp4j.Location;

/**
 * Symbol resolved at a source position. File-local symbols are only valid
 * for operations on the {@link AnalysisSession} that produced them.
 */
public interface ResolvedSymbol {

    SymbolIdentity identity();

    Optional<Location> definition();

    default boolean fileLocal() {
        return identity().fileLocal();
    }

    default String simpleName() {
        return identity().simpleName();
    }

    default Optional<String> originResourceUri() {
        return identity().originResourceUri();
    }
}
