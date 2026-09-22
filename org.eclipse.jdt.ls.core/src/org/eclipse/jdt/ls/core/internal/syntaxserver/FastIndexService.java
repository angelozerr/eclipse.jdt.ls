/*******************************************************************************
 * Copyright (c) 2026 Angelo Zerr and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo Zerr - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.syntaxserver;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.AnalysisSession;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.InMemoryIndex;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.SourceText;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.WorkspaceCompiler;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.ecj.EcjWorkspaceCompiler;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan.DirInput;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan.InputSource;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan.JarInput;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan.JrtInput;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan.ScanResult;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.scan.Scanner;

/**
 * Manages a lightweight declaration index built from the workspace classpath.
 *
 * <p>This service runs in the {@link SyntaxLanguageServer} to provide fast
 * cross-file navigation (find references, type hierarchy) while the full
 * JDT model is still initializing.
 *
 * <p>The index is built asynchronously on a background thread. Callers
 * can check {@link #isReady()} or obtain the index via {@link #index()}.
 */
public final class FastIndexService {

    private final AtomicReference<Index> indexRef = new AtomicReference<>();
    private final WorkspaceCompiler compiler = new EcjWorkspaceCompiler();
    private volatile boolean ready;
    private volatile boolean disposed;

    public boolean isReady() {
        return ready;
    }

    public Optional<Index> index() {
        Index idx = indexRef.get();
        return idx == null ? Optional.empty() : Optional.of(idx);
    }

    public ClasspathOrder classpath() {
        return ClasspathOrder.UNRESTRICTED;
    }

    public WorkspaceCompiler compiler() {
        return compiler;
    }

    /**
     * Build the index from workspace projects asynchronously.
     */
    public CompletableFuture<Void> buildIndexAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                buildIndex();
            } catch (Exception e) {
                JavaLanguageServerPlugin.logException("Fast index build failed", e);
            }
        });
    }

    private void buildIndex() {
        if (disposed) return;
        long t0 = System.currentTimeMillis();

        List<InputSource> sources = collectInputSources();
        if (sources.isEmpty()) {
            JavaLanguageServerPlugin.logInfo("Fast index: no input sources found");
            return;
        }

        InMemoryIndex index = new InMemoryIndex();
        Scanner scanner = new Scanner();
        ScanResult result = scanner.scan(sources, index);

        if (disposed) return;

        indexRef.set(index);
        ready = true;

        long elapsed = System.currentTimeMillis() - t0;
        JavaLanguageServerPlugin.logInfo(
                "Fast index ready: " + index.size() + " types, "
                        + index.entryCount() + " entries in " + elapsed + " ms"
                        + " (classes: " + result.classFilesMs() + " ms, sources: " + result.sourceFilesMs() + " ms)"
                        + (result.failures().isEmpty() ? "" : ", " + result.failures().size() + " failures"));
    }

    private List<InputSource> collectInputSources() {
        List<InputSource> sources = new ArrayList<>();
        Set<String> seenJars = new LinkedHashSet<>();
        Path javaHome = Path.of(System.getProperty("java.home"));

        sources.add(new JrtInput(javaHome));

        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            IJavaProject javaProject = JavaCore.create(project);
            if (javaProject == null || !javaProject.exists()) continue;
            try {
                collectFromProject(javaProject, sources, seenJars);
            } catch (JavaModelException e) {
                JavaLanguageServerPlugin.logException(
                        "Fast index: error reading classpath for " + project.getName(), e);
            }
        }
        return sources;
    }

    private void collectFromProject(IJavaProject javaProject, List<InputSource> sources,
                                    Set<String> seenJars) throws JavaModelException {
        IClasspathEntry[] classpath = javaProject.getRawClasspath();
        for (IClasspathEntry entry : classpath) {
            switch (entry.getEntryKind()) {
                case IClasspathEntry.CPE_SOURCE -> {
                    IPath srcPath = entry.getPath();
                    if (srcPath != null) {
                        IPath location = ResourcesPlugin.getWorkspace().getRoot()
                                .getFolder(srcPath).getLocation();
                        if (location != null) {
                            sources.add(new DirInput(Path.of(location.toOSString())));
                        }
                    }
                }
                case IClasspathEntry.CPE_LIBRARY -> {
                    IPath libPath = entry.getPath();
                    if (libPath != null) {
                        String jarPathStr = libPath.toOSString();
                        if (seenJars.add(jarPathStr) && jarPathStr.endsWith(".jar")) {
                            Path jarPath = Path.of(jarPathStr);
                            if (jarPath.toFile().exists()) {
                                sources.add(new JarInput(jarPath));
                            }
                        }
                    }
                }
                default -> {}
            }
        }
    }

    /**
     * Locate the source of a {@link TypeEntry} in the workspace.
     * Returns a {@link Location} pointing to the type declaration.
     */
    public Optional<Location> locateType(TypeEntry entry) {
        if (entry == null) return Optional.empty();
        String resourceUri = entry.resourceUri();
        if (resourceUri == null || resourceUri.isBlank()) return Optional.empty();
        Range range = new Range(new Position(0, 0), new Position(0, 0));
        return Optional.of(new Location(resourceUri, range));
    }

    /**
     * Analyze a source file against the index. Returns empty if the index
     * is not ready or the source cannot be read.
     */
    public Optional<AnalysisSession> analyze(String fileUri) {
        Index idx = indexRef.get();
        if (idx == null) return Optional.empty();
        String source = readSource(fileUri);
        if (source == null) return Optional.empty();
        try {
            URI uri = URI.create(fileUri);
            AnalysisSession session = compiler.analyze(uri, source, idx, classpath());
            return session.isUsable() ? Optional.of(session) : Optional.empty();
        } catch (Exception e) {
            JavaLanguageServerPlugin.logException("Fast index: analysis failed for " + fileUri, e);
            return Optional.empty();
        }
    }

    /**
     * Analyze source text already in hand (e.g. from an open editor buffer).
     */
    public Optional<AnalysisSession> analyze(String fileUri, String source) {
        Index idx = indexRef.get();
        if (idx == null || source == null) return Optional.empty();
        try {
            URI uri = URI.create(fileUri);
            AnalysisSession session = compiler.analyze(uri, source, idx, classpath());
            return session.isUsable() ? Optional.of(session) : Optional.empty();
        } catch (Exception e) {
            JavaLanguageServerPlugin.logException("Fast index: analysis failed for " + fileUri, e);
            return Optional.empty();
        }
    }

    private static String readSource(String fileUri) {
        return SourceText.read(fileUri);
    }

    public void dispose() {
        disposed = true;
        indexRef.set(null);
        ready = false;
    }
}
