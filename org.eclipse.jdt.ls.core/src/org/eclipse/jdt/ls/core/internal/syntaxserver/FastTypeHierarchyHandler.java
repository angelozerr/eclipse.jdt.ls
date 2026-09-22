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
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;

import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.AnalysisSession;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.SourceText;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.TypeHierarchySupport;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.WorkspaceCompiler;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;

/**
 * Handles typeHierarchy/prepare, typeHierarchy/supertypes, and
 * typeHierarchy/subtypes using the fast index and ECJ analysis.
 */
public final class FastTypeHierarchyHandler {

    private FastTypeHierarchyHandler() {}

    public static List<TypeHierarchyItem> prepare(TypeHierarchyPrepareParams params,
                                                  FastIndexService indexService) {
        if (!indexService.isReady()) return List.of();
        Optional<Index> indexOpt = indexService.index();
        if (indexOpt.isEmpty()) return List.of();

        Index index = indexOpt.get();
        ClasspathOrder classpath = indexService.classpath();
        WorkspaceCompiler compiler = indexService.compiler();

        String documentUri = FastReferencesHandler.decodeUri(params.getTextDocument().getUri());
        Position position = params.getPosition();

        String source = readSource(documentUri);
        if (source == null) return List.of();

        URI uri = toURI(documentUri);
        AnalysisSession session = compiler.analyze(uri, source, index, classpath);
        if (!session.isUsable()) return List.of();

        Optional<TypeHierarchyItem> item = session.prepareTypeHierarchy(position);
        return item.map(List::of).orElse(List.of());
    }

    public static List<TypeHierarchyItem> supertypes(TypeHierarchySupertypesParams params,
                                                     FastIndexService indexService) {
        if (!indexService.isReady()) return List.of();
        Optional<Index> indexOpt = indexService.index();
        if (indexOpt.isEmpty()) return List.of();

        return TypeHierarchySupport.directSupertypes(
                params.getItem(), indexOpt.get(), indexService.classpath(),
                indexService::locateType);
    }

    public static List<TypeHierarchyItem> subtypes(TypeHierarchySubtypesParams params,
                                                   FastIndexService indexService) {
        if (!indexService.isReady()) return List.of();
        Optional<Index> indexOpt = indexService.index();
        if (indexOpt.isEmpty()) return List.of();

        return TypeHierarchySupport.directSubtypes(
                params.getItem(), indexOpt.get(), indexService.classpath(),
                indexService::locateType);
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

    private static URI toURI(String uri) {
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
