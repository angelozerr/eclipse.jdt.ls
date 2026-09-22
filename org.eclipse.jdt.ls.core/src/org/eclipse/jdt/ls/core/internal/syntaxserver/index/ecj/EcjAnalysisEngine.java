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
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.PublishedDiagnostic;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;

final class EcjAnalysisEngine {
    private static final String OBJECT_JVM_NAME = "java/lang/Object";

    private EcjAnalysisEngine() {}

    static EcjAnalysisSession analyze(URI uri,
                                      CharSequence text,
                                      Index index,
                                      ClasspathOrder classpath,
                                      EcjDeclarationLocator declarationLocator,
                                      Map<String, String> sourceJarByBinaryJar) {
        if (!index.contains(OBJECT_JVM_NAME)) return EcjAnalysisSession.empty();

        String source = text == null ? "" : text.toString();
        String fileName = toFileName(uri);
        ICompilationUnit input = new CompilationUnit(source.toCharArray(), fileName, "UTF-8");
        List<CategorizedProblem> problems = new ArrayList<>();
        ICompilerRequestor requestor = result -> collectProblems(result, problems);
        CompilerOptions options = new CompilerOptions();
        options.generateClassFiles = false;
        options.preserveAllLocalVariables = true;
        options.performMethodsFullRecovery = true;
        options.performStatementsRecovery = true;
        options.produceReferenceInfo = true;
        options.maxProblemsPerUnit = Integer.MAX_VALUE;
        options.sourceLevel = CompilerOptions.versionToJdkLevel(CompilerOptions.getLatestVersion());
        options.complianceLevel = options.sourceLevel;
        options.targetJDK = options.sourceLevel;

        IndexNameEnvironment environment = new IndexNameEnvironment(index, classpath);
        CapturingCompiler compiler = new CapturingCompiler(environment, options, requestor);
        try {
            compiler.compile(new ICompilationUnit[] { input });
            mergeUnitProblems(compiler.unit, problems);
            return new EcjAnalysisSession(uri, source, compiler.unit, mapProblems(problems, source),
                    index, classpath, declarationLocator, sourceJarByBinaryJar);
        } catch (RuntimeException | Error failure) {
            mergeUnitProblems(compiler.unit, problems);
            List<PublishedDiagnostic> diagnostics = mapProblems(problems, source);
            if (!diagnostics.isEmpty() || compiler.unit != null) {
                return new EcjAnalysisSession(uri, source, compiler.unit, diagnostics,
                        index, classpath, declarationLocator, sourceJarByBinaryJar);
            }
            return EcjAnalysisSession.empty();
        } finally {
            environment.cleanup();
        }
    }

    private static void collectProblems(CompilationResult result, List<CategorizedProblem> out) {
        CategorizedProblem[] found = result.getProblems();
        if (found != null) {
            for (CategorizedProblem problem : found) out.add(problem);
        }
    }

    private static void mergeUnitProblems(CompilationUnitDeclaration unit, List<CategorizedProblem> problems) {
        if (unit == null || unit.compilationResult == null) return;
        CategorizedProblem[] all = unit.compilationResult.getAllProblems();
        if (all == null) return;
        Set<CategorizedProblem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(problems);
        for (CategorizedProblem problem : all) {
            if (problem != null && seen.add(problem)) problems.add(problem);
        }
    }

    private static List<PublishedDiagnostic> mapProblems(List<CategorizedProblem> problems, String source) {
        List<PublishedDiagnostic> out = new ArrayList<>(problems.size());
        for (CategorizedProblem problem : problems) {
            int start = Math.max(0, problem.getSourceStart());
            int end = Math.max(start, problem.getSourceEnd());
            out.add(new PublishedDiagnostic(
                    new Range(positionAt(source, start), positionAt(source, Math.min(source.length(), end + 1))),
                    problem.getMessage(),
                    severity(problem),
                    "ecj",
                    Integer.toString(problem.getID())));
        }
        return List.copyOf(out);
    }

    static Position positionAt(String source, int offset) {
        int bounded = Math.max(0, Math.min(source.length(), offset));
        int line = 0;
        int column = 0;
        for (int i = 0; i < bounded; i++) {
            char c = source.charAt(i);
            if (c == '\n') {
                line++;
                column = 0;
            } else if (c != '\r') {
                column++;
            }
        }
        return new Position(line, column);
    }

    static int offsetAt(String source, Position position) {
        if (position == null || position.getLine() < 0 || position.getCharacter() < 0) return -1;
        int line = 0;
        int offset = 0;
        while (offset < source.length() && line < position.getLine()) {
            if (source.charAt(offset++) == '\n') line++;
        }
        if (line != position.getLine()) return -1;
        return Math.min(source.length(), offset + position.getCharacter());
    }

    private static String toFileName(URI uri) {
        if (uri == null) return "Analysis.java";
        if ("file".equals(uri.getScheme())) {
            try {
                return java.nio.file.Path.of(uri).toString();
            } catch (Exception ignored) {
            }
        }
        return uri.toString();
    }

    private static DiagnosticSeverity severity(CategorizedProblem problem) {
        if (problem.isError()) return DiagnosticSeverity.Error;
        if (problem.isWarning()) return DiagnosticSeverity.Warning;
        if (problem.isInfo()) return DiagnosticSeverity.Information;
        return DiagnosticSeverity.Hint;
    }

    private static final class CapturingCompiler extends Compiler {
        private CompilationUnitDeclaration unit;

        CapturingCompiler(IndexNameEnvironment environment,
                          CompilerOptions options,
                          ICompilerRequestor requestor) {
            super(environment,
                    DefaultErrorHandlingPolicies.proceedWithAllProblems(),
                    options,
                    requestor,
                    new DefaultProblemFactory(Locale.ROOT));
        }

        @Override
        protected void processCompiledUnits(int startingIndex, boolean lastRound) {
            CompilationUnitDeclaration current = null;
            try {
                for (int i = startingIndex; i < totalUnits; i++) {
                    current = unitsToProcess[i];
                    if (current.compilationResult != null && current.compilationResult.hasBeenAccepted) continue;
                    unit = current;
                    process(current, i);
                    requestor.acceptResult(current.compilationResult.tagAsAccepted());
                }
            } catch (AbortCompilation e) {
                handleInternalException(e, current);
            } catch (Error | RuntimeException e) {
                handleInternalException(e, current, null);
                throw e;
            }
        }
    }
}
