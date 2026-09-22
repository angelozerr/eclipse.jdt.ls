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

import java.util.Objects;
import java.util.Optional;

/**
 * Backend-neutral symbol identity for cross-file reference matching.
 * Key format matches the historical javac {@code SymbolKey} encoding.
 */
public record SymbolIdentity(
        String matchKey,
        String simpleName,
        boolean fileLocal,
        Optional<String> originResourceUri) {

    public SymbolIdentity {
        Objects.requireNonNull(simpleName, "simpleName");
        Objects.requireNonNull(originResourceUri, "originResourceUri");
        if (!fileLocal) {
            Objects.requireNonNull(matchKey, "matchKey");
        }
    }

    public boolean matches(SymbolIdentity other) {
        if (other == null) return false;
        if (fileLocal || other.fileLocal) return false;
        return matchKey.equals(other.matchKey);
    }
}
