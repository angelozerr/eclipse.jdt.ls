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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.ecj;

import java.net.URI;
import java.util.Map;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.AnalysisSession;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.WorkspaceCompiler;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;

/**
 * ECJ-backed workspace compiler using {@link IndexNameEnvironment} instead
 * of javac's IndexFileManager / IndexClassReader.
 */
public final class EcjWorkspaceCompiler implements WorkspaceCompiler {

    private final EcjDeclarationLocator declarationLocator;
    private final Map<String, String> sourceJarByBinaryJar;

    public EcjWorkspaceCompiler() {
        this(new EcjDeclarationLocator(), Map.of());
    }

    /**
     * @param sourceJarByBinaryJar sources archive per binary classpath
     *        container, keyed as the indexer stamps container URIs on its
     *        entries; without it, declarations in jars and the JDK have no
     *        navigable source
     */
    public EcjWorkspaceCompiler(EcjDeclarationLocator declarationLocator,
                                Map<String, String> sourceJarByBinaryJar) {
        this.declarationLocator = declarationLocator == null ? new EcjDeclarationLocator() : declarationLocator;
        this.sourceJarByBinaryJar = sourceJarByBinaryJar == null ? Map.of() : sourceJarByBinaryJar;
    }

    @Override
    public AnalysisSession analyze(URI uri, CharSequence text, Index index, ClasspathOrder classpath) {
        return EcjAnalysisEngine.analyze(uri, text, index, classpath, declarationLocator, sourceJarByBinaryJar);
    }
}
