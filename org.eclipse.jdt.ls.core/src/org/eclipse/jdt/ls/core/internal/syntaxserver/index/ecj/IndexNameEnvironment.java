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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry;

/**
 * ECJ name environment backed exclusively by the declaration index.
 *
 * <p>Indexed declarations are exposed as {@link IBinaryType} adapters over
 * {@link TypeEntry}. Both source- and classfile-derived entries are treated
 * as binary types for binding construction (mirroring javac's
 * {@code IndexClassReader}).
 */
final class IndexNameEnvironment implements INameEnvironment {
    private final Index index;
    private final ClasspathOrder classpath;
    private final Map<String, NameEnvironmentAnswer> answers = new ConcurrentHashMap<>();

    IndexNameEnvironment(Index index, ClasspathOrder classpath) {
        this.index = index;
        this.classpath = classpath == null ? ClasspathOrder.UNRESTRICTED : classpath;
    }

    @Override
    public NameEnvironmentAnswer findType(char[][] compoundName) {
        if (compoundName == null || compoundName.length == 0) return null;
        StringBuilder name = new StringBuilder();
        for (char[] component : compoundName) {
            if (!name.isEmpty()) name.append('/');
            name.append(component);
        }
        return find(name.toString());
    }

    @Override
    public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
        StringBuilder name = new StringBuilder();
        if (packageName != null) {
            for (char[] component : packageName) {
                if (!name.isEmpty()) name.append('/');
                name.append(component);
            }
        }
        if (!name.isEmpty()) name.append('/');
        name.append(typeName);
        return find(name.toString());
    }

    private NameEnvironmentAnswer find(String jvmName) {
        NameEnvironmentAnswer cached = answers.get(jvmName);
        if (cached != null) return cached;
        TypeEntry winner = classpath.pick(index.getAll(jvmName), TypeEntry::sourceUri);
        if (winner == null) return null;
        try {
            IBinaryType binary = IndexBinaryType.of(winner, index, classpath);
            NameEnvironmentAnswer made = new NameEnvironmentAnswer(binary, null);
            NameEnvironmentAnswer prior = answers.putIfAbsent(jvmName, made);
            return prior == null ? made : prior;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public boolean isPackage(char[][] parentPackageName, char[] packageName) {
        StringBuilder name = new StringBuilder();
        if (parentPackageName != null) {
            for (char[] component : parentPackageName) {
                if (!name.isEmpty()) name.append('/');
                name.append(component);
            }
        }
        if (!name.isEmpty()) name.append('/');
        name.append(packageName);
        return index.hasPackage(name.toString());
    }

    @Override
    public void cleanup() {
        answers.clear();
    }
}
