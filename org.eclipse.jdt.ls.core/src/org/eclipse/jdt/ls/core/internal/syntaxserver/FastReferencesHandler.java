/*******************************************************************************
 * Copyright (c) 2026 Angelo Zerr and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.syntaxserver;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceParams;

import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.AnalysisSession;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.ResolvedSymbol;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.SourceText;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.SymbolIdentity;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.WorkspaceCompiler;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.bloom.BloomEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;

/**
 * Handles textDocument/references using the fast index and ECJ analysis.
 */
public final class FastReferencesHandler {

    private FastReferencesHandler() {}

    public static List<? extends Location> references(ReferenceParams params,
                                                      FastIndexService indexService) {
        if (!indexService.isReady()) {
            JavaLanguageServerPlugin.logInfo("FastReferences: index not ready");
            return List.of();
        }
        Optional<Index> indexOpt = indexService.index();
        if (indexOpt.isEmpty()) {
            JavaLanguageServerPlugin.logInfo("FastReferences: index is empty");
            return List.of();
        }

        Index index = indexOpt.get();
        ClasspathOrder classpath = indexService.classpath();
        WorkspaceCompiler compiler = indexService.compiler();

        String documentUri = decodeUri(params.getTextDocument().getUri());
        Position position = params.getPosition();

        String source = readSource(documentUri);
        if (source == null) {
            JavaLanguageServerPlugin.logInfo("FastReferences: could not read source for " + documentUri);
            return List.of();
        }

        URI uri = toURI(documentUri);
        AnalysisSession session = compiler.analyze(uri, source, index, classpath);
        if (!session.isUsable()) {
            JavaLanguageServerPlugin.logInfo("FastReferences: session not usable for " + documentUri
                    + ", diagnostics: " + session.diagnostics().size());
            return List.of();
        }

        Optional<ResolvedSymbol> resolved = session.resolveAt(position);
        if (resolved.isEmpty()) {
            JavaLanguageServerPlugin.logInfo("FastReferences: could not resolve symbol at "
                    + position.getLine() + ":" + position.getCharacter());
            return List.of();
        }

        ResolvedSymbol symbol = resolved.get();

        if (symbol.fileLocal()) {
            List<Location> localRefs = session.referencesInUnit(symbol);
            JavaLanguageServerPlugin.logInfo("FastReferences: file-local symbol '"
                    + symbol.simpleName() + "', " + localRefs.size() + " references");
            return localRefs;
        }

        SymbolIdentity identity = symbol.identity();
        JavaLanguageServerPlugin.logInfo("FastReferences: cross-file symbol '"
                + identity.simpleName() + "', matchKey=" + identity.matchKey());

        Set<String> candidateUris = bloomCandidates(index, identity.simpleName());
        JavaLanguageServerPlugin.logInfo("FastReferences: " + candidateUris.size()
                + " bloom candidates, total bloom filters: " + index.bloomFilters().size());

        List<Location> results = new ArrayList<>();
        results.addAll(session.referencesInUnit(symbol));

        for (String candidateUri : candidateUris) {
            if (candidateUri.equals(documentUri)) {
                JavaLanguageServerPlugin.logInfo("FastReferences: skipping current file: " + candidateUri);
                continue;
            }
            String candidateSource = SourceText.read(candidateUri);
            if (candidateSource == null) {
                JavaLanguageServerPlugin.logInfo("FastReferences: could not read candidate: " + candidateUri);
                continue;
            }
            URI candidateJavaUri = toURI(candidateUri);
            AnalysisSession candidateSession = compiler.analyze(candidateJavaUri, candidateSource, index, classpath);
            if (!candidateSession.isUsable()) {
                JavaLanguageServerPlugin.logInfo("FastReferences: candidate session not usable: " + candidateUri);
                continue;
            }
            List<Location> found = candidateSession.findReferencesTo(identity);
            JavaLanguageServerPlugin.logInfo("FastReferences: candidate " + candidateUri
                    + " -> " + found.size() + " refs");
            results.addAll(found);
        }

        JavaLanguageServerPlugin.logInfo("FastReferences: total " + results.size() + " references found");
        return results;
    }

    private static Set<String> bloomCandidates(Index index, String simpleName) {
        Set<String> uris = new LinkedHashSet<>();
        for (BloomEntry entry : index.bloomFilters()) {
            String path = entry.resourcePath();
            if (path != null && path.endsWith(".java") && entry.filter().mightContain(simpleName)) {
                String uri = entry.resourceUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        }
        return uris;
    }

    private static String readSource(String uri) {
        try {
            org.eclipse.jdt.core.ITypeRoot typeRoot = JDTUtils.resolveTypeRoot(uri);
            if (typeRoot != null && typeRoot.getBuffer() != null) {
                return typeRoot.getBuffer().getContents();
            }
        } catch (Exception ignored) {
        }
        return SourceText.read(uri);
    }

    static String decodeUri(String uri) {
        if (uri == null) return null;
        String decoded = URLDecoder.decode(uri, StandardCharsets.UTF_8);
        while (!uri.equals(decoded)) {
            uri = decoded;
            decoded = URLDecoder.decode(uri, StandardCharsets.UTF_8);
        }
        return decoded;
    }

    private static URI toURI(String uri) {
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
