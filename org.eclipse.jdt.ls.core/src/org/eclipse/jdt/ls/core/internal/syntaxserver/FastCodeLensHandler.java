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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.AnalysisSession;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Declaration;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.SourceText;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.SymbolIdentity;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.WorkspaceCompiler;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.bloom.BloomEntry;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;

import com.google.gson.JsonObject;

public final class FastCodeLensHandler {

    private FastCodeLensHandler() {}

    public static List<? extends CodeLens> codeLens(String uri,
                                                     FastIndexService indexService,
                                                     PreferenceManager preferenceManager) {
        if (!preferenceManager.getPreferences().isReferencesCodeLensEnabled()) {
            return List.of();
        }
        if (!indexService.isReady()) {
			return List.of();
		}

        String documentUri = FastReferencesHandler.decodeUri(uri);
        Optional<AnalysisSession> sessionOpt = indexService.analyze(documentUri);
        if (sessionOpt.isEmpty()) {
			return List.of();
		}

        AnalysisSession session = sessionOpt.get();
        boolean includeFields = preferenceManager.getPreferences().isReferencesCodeLensIncludeFields();

        List<Declaration> declarations = session.declarations();
        List<CodeLens> lenses = new ArrayList<>();
        for (Declaration decl : declarations) {
            if (decl.kind() == Declaration.Kind.FIELD && !includeFields) {
				continue;
			}
            CodeLens lens = new CodeLens(decl.nameRange());
            lens.setData(codeLensData(documentUri, decl.identity()));
            lenses.add(lens);
        }
        return lenses;
    }

    public static CodeLens resolve(CodeLens codeLens,
                                    FastIndexService indexService) {
        if (!(codeLens.getData() instanceof JsonObject data)) {
            return codeLens;
        }
        SymbolIdentity identity = identityFromData(data);
        if (identity == null) {
            codeLens.setCommand(new Command("0 references", "editor.action.showReferences"));
            return codeLens;
        }

        String uri = data.has("uri") ? data.get("uri").getAsString() : "";
        Optional<Index> indexOpt = indexService.index();
        if (indexOpt.isEmpty()) {
            codeLens.setCommand(new Command("0 references", "editor.action.showReferences"));
            return codeLens;
        }

        Index index = indexOpt.get();
        ClasspathOrder classpath = indexService.classpath();
        WorkspaceCompiler compiler = indexService.compiler();

        Set<String> candidates = bloomCandidates(index, identity.simpleName());
        List<Location> locations = new ArrayList<>();

        for (String candidateUri : candidates) {
            String candidateSource = SourceText.read(candidateUri);
            if (candidateSource == null) {
				continue;
			}
            URI candidateJavaUri;
            try {
                candidateJavaUri = URI.create(candidateUri);
            } catch (IllegalArgumentException e) {
                continue;
            }
            AnalysisSession candidateSession = compiler.analyze(candidateJavaUri, candidateSource, index, classpath);
            if (!candidateSession.isUsable()) {
				continue;
			}
            locations.addAll(candidateSession.findReferencesTo(identity));
        }

        int count = locations.size();
        String title = count == 1 ? "1 reference" : count + " references";
        Command command = new Command(title, "editor.action.showReferences");
        command.setArguments(List.of(uri, codeLens.getRange().getStart(), new ArrayList<>(locations)));
        codeLens.setCommand(command);
        return codeLens;
    }

    private static Set<String> bloomCandidates(Index index, String simpleName) {
        Set<String> uris = new LinkedHashSet<>();
        for (BloomEntry entry : index.bloomFilters()) {
            String path = entry.resourcePath();
            if (path != null && path.endsWith(".java") && entry.filter().mightContain(simpleName)) {
                String entryUri = entry.resourceUri();
                if (entryUri != null) {
                    uris.add(entryUri);
                }
            }
        }
        return uris;
    }

    private static JsonObject codeLensData(String uri, SymbolIdentity identity) {
        JsonObject data = new JsonObject();
        data.addProperty("uri", uri);
        data.addProperty("matchKey", identity.matchKey());
        data.addProperty("simpleName", identity.simpleName());
        identity.originResourceUri().ifPresent(o -> data.addProperty("originResourceUri", o));
        return data;
    }

    private static SymbolIdentity identityFromData(JsonObject data) {
        if (!data.has("matchKey") || !data.has("simpleName")) {
			return null;
		}
        String matchKey = data.get("matchKey").getAsString();
        String simpleName = data.get("simpleName").getAsString();
        Optional<String> origin = data.has("originResourceUri")
                ? Optional.of(data.get("originResourceUri").getAsString())
                : Optional.empty();
        return new SymbolIdentity(matchKey, simpleName, false, origin);
    }
}
