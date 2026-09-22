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
package org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;

/** Directory on the local filesystem, walked recursively. */
public record DirInput(Path root, ScanCollector collector) implements InputSource {

    public DirInput(Path root) {
        this(root, null);
    }

    @Override
    public void walk(ResourceSink sink) {
        if (!Files.exists(root)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName().toString();
                    if (isIndexable(name)) {
                        String relativePath = root.relativize(file).toString().replace('\\', '/');
                        recordStats(name, attrs.size());
                        sink.accept(relativePath, name, () -> Files.readAllBytes(file));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed walking " + root, e);
        }
    }

    private void recordStats(String name, long size) {
        if (collector == null) return;
        if (name.endsWith(".java")) {
            collector.addSourceFile();
        } else if (name.endsWith(".class")) {
            collector.addClassFileBytes(size);
        }
    }

    @Override
    public String sourceUri() {
        return root.toUri().toString();
    }

    private static boolean isIndexable(String name) {
        return (name.endsWith(".java") || name.endsWith(".class")) && !Index.isSkippedFileName(name);
    }
}
