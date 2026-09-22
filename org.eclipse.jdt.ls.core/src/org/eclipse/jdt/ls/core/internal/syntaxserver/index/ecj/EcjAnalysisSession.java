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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.ArrayQualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.ImportReference;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.ImportBinding;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TypeHierarchyItem;

import org.eclipse.jdt.ls.core.internal.syntaxserver.index.AnalysisSession;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Declaration;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.Index;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.PublishedDiagnostic;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.ResolvedSymbol;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.SymbolIdentity;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.TypeHierarchySupport;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.classpath.ClasspathOrder;
import org.eclipse.jdt.ls.core.internal.syntaxserver.index.model.TypeEntry;

final class EcjAnalysisSession implements AnalysisSession {

    private final URI uri;
    private final String source;
    private final CompilationUnitDeclaration unit;
    private final List<PublishedDiagnostic> diagnostics;
    private final Index index;
    private final ClasspathOrder classpath;
    private final EcjDeclarationLocator declarationLocator;
    private final Map<String, String> sourceJarByBinaryJar;

    EcjAnalysisSession(URI uri,
                       String source,
                       CompilationUnitDeclaration unit,
                       List<PublishedDiagnostic> diagnostics,
                       Index index,
                       ClasspathOrder classpath,
                       EcjDeclarationLocator declarationLocator,
                       Map<String, String> sourceJarByBinaryJar) {
        this.uri = uri;
        this.source = source;
        this.unit = unit;
        this.diagnostics = diagnostics;
        this.index = index;
        this.classpath = classpath == null ? ClasspathOrder.UNRESTRICTED : classpath;
        this.declarationLocator = declarationLocator;
        this.sourceJarByBinaryJar = sourceJarByBinaryJar == null ? Map.of() : sourceJarByBinaryJar;
    }

    static EcjAnalysisSession empty() {
        return new EcjAnalysisSession(null, "", null, List.of(), null, ClasspathOrder.UNRESTRICTED,
                null, Map.of());
    }

    @Override
    public List<PublishedDiagnostic> diagnostics() {
        return diagnostics;
    }

    @Override
    public Optional<ResolvedSymbol> resolveAt(Position position) {
        if (!isUsable()) return Optional.empty();
        int offset = EcjAnalysisEngine.offsetAt(source, position);
        if (offset < 0) return Optional.empty();
        SymbolOccurrence occurrence = findOccurrenceAt(offset);
        if (occurrence == null || occurrence.binding == null) return Optional.empty();
        SymbolIdentity identity = identityOf(occurrence.binding);
        if (identity == null) return Optional.empty();
        Optional<Location> definition = definitionFor(occurrence.binding);
        return Optional.of(new EcjResolvedSymbol(identity, definition, occurrence.binding));
    }

    @Override
    public List<Location> referencesInUnit(ResolvedSymbol symbol) {
        if (!isUsable() || !(symbol instanceof EcjResolvedSymbol ecj)) return List.of();
        return references(ecj.identity(), ecj.binding());
    }

    @Override
    public List<Location> findReferencesTo(SymbolIdentity identity) {
        if (!isUsable() || identity == null || identity.fileLocal()) return List.of();
        return references(identity, null);
    }

    @Override
    public Optional<Location> definitionOf(ResolvedSymbol symbol) {
        if (symbol == null) return Optional.empty();
        if (symbol.definition().isPresent()) return symbol.definition();
        if (symbol instanceof EcjResolvedSymbol ecj) {
            return definitionFor(ecj.binding());
        }
        return Optional.empty();
    }

    @Override
    public Optional<TypeHierarchyItem> prepareTypeHierarchy(Position position) {
        Optional<ResolvedSymbol> resolved = resolveAt(position);
        if (resolved.isEmpty() || !(resolved.get() instanceof EcjResolvedSymbol ecj)) {
            return Optional.empty();
        }
        Binding binding = ecj.binding();
        if (!(binding instanceof ReferenceBinding reference) || !isNamedType(reference)) {
            return Optional.empty();
        }
        Optional<Location> location = definitionOf(ecj);
        if (location.isEmpty()) return Optional.empty();
        return TypeHierarchySupport.itemForResolved(ecj, location.get(), symbolKind(reference));
    }

    @Override
    public List<TypeHierarchyItem> typeHierarchySupertypes(TypeHierarchyItem item) {
        if (index == null) return List.of();
        return TypeHierarchySupport.directSupertypes(item, index, classpath, this::locateTypeEntry);
    }

    @Override
    public List<TypeHierarchyItem> typeHierarchySubtypes(TypeHierarchyItem item) {
        if (index == null) return List.of();
        return TypeHierarchySupport.directSubtypes(item, index, classpath, this::locateTypeEntry);
    }

    private Optional<Location> locateTypeEntry(TypeEntry entry) {
        if (declarationLocator == null) return Optional.empty();
        return declarationLocator.locateType(entry, sourceJarByBinaryJar);
    }

    private static boolean isNamedType(ReferenceBinding binding) {
        return binding.isClass() || binding.isInterface() || binding.isEnum() || binding.isRecord();
    }

    private static SymbolKind symbolKind(ReferenceBinding binding) {
        if (binding.isAnnotationType() || binding.isInterface()) return SymbolKind.Interface;
        if (binding.isEnum()) return SymbolKind.Enum;
        return SymbolKind.Class;
    }

    @Override
    public List<Declaration> declarations() {
        if (!isUsable()) return List.of();
        List<Declaration> result = new ArrayList<>();
        visitOccurrences(occurrence -> {
            if (!occurrence.declaration()) return;
            Declaration.Kind kind = declarationKind(occurrence.binding());
            if (kind == null) return;
            SymbolIdentity identity = identityOf(occurrence.binding());
            if (identity == null || identity.fileLocal()) return;
            Range range = new Range(
                    EcjAnalysisEngine.positionAt(source, occurrence.start()),
                    EcjAnalysisEngine.positionAt(source, occurrence.end() + 1));
            result.add(new Declaration(identity.simpleName(), range, identity, kind));
        });
        return result;
    }

    private static Declaration.Kind declarationKind(Binding binding) {
        if (binding instanceof ReferenceBinding) return Declaration.Kind.TYPE;
        if (binding instanceof MethodBinding) return Declaration.Kind.METHOD;
        if (binding instanceof FieldBinding) return Declaration.Kind.FIELD;
        return null;
    }

    @Override
    public boolean isUsable() {
        return unit != null && unit.scope != null;
    }

    private List<Location> references(SymbolIdentity target, Binding exactBinding) {
        Set<Location> found = new LinkedHashSet<>();
        visitOccurrences(occurrence -> {
            SymbolIdentity candidate = identityOf(occurrence.binding);
            boolean matches = exactBinding != null
                    ? occurrence.binding == exactBinding || sameBinding(occurrence.binding, exactBinding)
                    : candidate != null && target.matches(candidate);
            if (matches) found.add(location(occurrence.start, occurrence.end));
        });
        return List.copyOf(found);
    }

    private SymbolOccurrence findOccurrenceAt(int offset) {
        SymbolOccurrence[] best = new SymbolOccurrence[1];
        visitOccurrences(candidate -> {
            if (offset < candidate.start || offset > candidate.end) return;
            if (best[0] == null
                    || candidate.end - candidate.start < best[0].end - best[0].start) {
                best[0] = candidate;
            }
        });
        return best[0];
    }

    private void visitOccurrences(java.util.function.Consumer<SymbolOccurrence> consumer) {
        unit.traverse(new ASTVisitor() {
            @Override
            public boolean visit(SingleNameReference node, BlockScope scope) {
                accept(consumer, node.binding, node.sourceStart, node.sourceEnd);
                return true;
            }

            @Override
            public boolean visit(SingleNameReference node, ClassScope scope) {
                accept(consumer, node.binding, node.sourceStart, node.sourceEnd);
                return true;
            }

            @Override
            public boolean visit(QualifiedNameReference node, BlockScope scope) {
                qualified(consumer, node);
                return true;
            }

            @Override
            public boolean visit(QualifiedNameReference node, ClassScope scope) {
                qualified(consumer, node);
                return true;
            }

            @Override
            public boolean visit(MessageSend node, BlockScope scope) {
                accept(consumer, node.binding, high(node.nameSourcePosition), low(node.nameSourcePosition));
                return true;
            }

            @Override
            public boolean visit(FieldReference node, BlockScope scope) {
                accept(consumer, node.binding, high(node.nameSourcePosition), low(node.nameSourcePosition));
                return true;
            }

            @Override
            public boolean visit(FieldReference node, ClassScope scope) {
                accept(consumer, node.binding, high(node.nameSourcePosition), low(node.nameSourcePosition));
                return true;
            }

            @Override
            public boolean visit(SingleTypeReference node, BlockScope scope) {
                accept(consumer, validType(node.resolvedType), node.sourceStart, node.sourceEnd);
                return true;
            }

            @Override
            public boolean visit(SingleTypeReference node, ClassScope scope) {
                accept(consumer, validType(node.resolvedType), node.sourceStart, node.sourceEnd);
                return true;
            }

            @Override
            public boolean visit(ArrayTypeReference node, BlockScope scope) {
                singleTypeName(consumer, node);
                return true;
            }

            @Override
            public boolean visit(ArrayTypeReference node, ClassScope scope) {
                singleTypeName(consumer, node);
                return true;
            }

            @Override
            public boolean visit(ParameterizedSingleTypeReference node, BlockScope scope) {
                singleTypeName(consumer, node);
                return true;
            }

            @Override
            public boolean visit(ParameterizedSingleTypeReference node, ClassScope scope) {
                singleTypeName(consumer, node);
                return true;
            }

            @Override
            public boolean visit(QualifiedTypeReference node, BlockScope scope) {
                accept(consumer, validType(node.resolvedType), node.sourceStart, node.sourceEnd);
                return true;
            }

            @Override
            public boolean visit(QualifiedTypeReference node, ClassScope scope) {
                accept(consumer, validType(node.resolvedType), node.sourceStart, node.sourceEnd);
                return true;
            }

            @Override
            public boolean visit(ArrayQualifiedTypeReference node, BlockScope scope) {
                qualifiedTypeName(consumer, node);
                return true;
            }

            @Override
            public boolean visit(ArrayQualifiedTypeReference node, ClassScope scope) {
                qualifiedTypeName(consumer, node);
                return true;
            }

            @Override
            public boolean visit(ParameterizedQualifiedTypeReference node, BlockScope scope) {
                qualifiedTypeName(consumer, node);
                return true;
            }

            @Override
            public boolean visit(ParameterizedQualifiedTypeReference node, ClassScope scope) {
                qualifiedTypeName(consumer, node);
                return true;
            }

            @Override
            public boolean visit(ImportReference node, CompilationUnitScope scope) {
                imported(consumer, node, scope);
                return true;
            }

            @Override
            public boolean visit(LocalDeclaration node, BlockScope scope) {
                declare(consumer, node.binding, node.sourceStart,
                        node.sourceStart + (node.name == null ? 0 : node.name.length - 1));
                return true;
            }

            @Override
            public boolean visit(Argument node, BlockScope scope) {
                declare(consumer, node.binding, node.sourceStart,
                        node.sourceStart + (node.name == null ? 0 : node.name.length - 1));
                return true;
            }

            @Override
            public boolean visit(TypeDeclaration node, ClassScope scope) {
                int start = findName(node.name, node.sourceStart, node.sourceEnd);
                declare(consumer, node.binding, start, start + node.name.length - 1);
                return true;
            }

            @Override
            public boolean visit(TypeDeclaration node, CompilationUnitScope scope) {
                int start = findName(node.name, node.sourceStart, node.sourceEnd);
                declare(consumer, node.binding, start, start + node.name.length - 1);
                return true;
            }

            @Override
            public boolean visit(FieldDeclaration node, org.eclipse.jdt.internal.compiler.lookup.MethodScope scope) {
                int start = findName(node.name, node.sourceStart, node.sourceEnd);
                declare(consumer, node.binding, start, start + node.name.length - 1);
                return true;
            }

            @Override
            public boolean visit(MethodDeclaration node, ClassScope scope) {
                int start = findName(node.selector, node.sourceStart, node.sourceEnd);
                declare(consumer, node.binding, start, start + node.selector.length - 1);
                return true;
            }

            @Override
            public boolean visit(ConstructorDeclaration node, ClassScope scope) {
                int start = findName(node.selector, node.sourceStart, node.sourceEnd);
                declare(consumer, node.binding, start, start + node.selector.length - 1);
                return true;
            }
        }, unit.scope, false);
    }

    private static void qualified(java.util.function.Consumer<SymbolOccurrence> consumer,
                                  QualifiedNameReference node) {
        Binding binding = node.lastFieldBinding();
        long position = node.sourcePositions[node.sourcePositions.length - 1];
        accept(consumer, binding, high(position), low(position));
    }

    private static void singleTypeName(java.util.function.Consumer<SymbolOccurrence> consumer,
                                       SingleTypeReference node) {
        int end = node.token == null || node.token.length == 0
                ? node.sourceEnd
                : node.sourceStart + node.token.length - 1;
        if (node instanceof ArrayTypeReference array && array.originalSourceEnd >= node.sourceStart) {
            end = array.originalSourceEnd;
        }
        accept(consumer, validType(node.resolvedType), node.sourceStart, end);
    }

    private static void qualifiedTypeName(java.util.function.Consumer<SymbolOccurrence> consumer,
                                          QualifiedTypeReference node) {
        if (node.sourcePositions == null || node.sourcePositions.length == 0) {
            accept(consumer, validType(node.resolvedType), node.sourceStart, node.sourceEnd);
            return;
        }
        long position = node.sourcePositions[node.sourcePositions.length - 1];
        accept(consumer, validType(node.resolvedType), high(position), low(position));
    }

    private static void imported(java.util.function.Consumer<SymbolOccurrence> consumer,
                                 ImportReference node,
                                 CompilationUnitScope scope) {
        if (node.sourcePositions == null || node.sourcePositions.length == 0) return;
        Binding resolved = resolvedImport(node, scope);
        long last = node.sourcePositions[node.sourcePositions.length - 1];
        if (resolved instanceof TypeBinding type) {
            accept(consumer, validType(type), high(last), low(last));
            return;
        }
        ReferenceBinding owner = importedMemberOwner(resolved);
        if (owner == null) return;
        accept(consumer, resolved, high(last), low(last));
        if (node.sourcePositions.length >= 2) {
            long ownerPosition = node.sourcePositions[node.sourcePositions.length - 2];
            accept(consumer, owner.erasure(), high(ownerPosition), low(ownerPosition));
        }
    }

    private static ReferenceBinding importedMemberOwner(Binding binding) {
        if (binding instanceof MethodBinding method) return method.declaringClass;
        if (binding instanceof FieldBinding field) return field.declaringClass;
        return null;
    }

    private static Binding resolvedImport(ImportReference node, CompilationUnitScope scope) {
        if (scope == null || scope.imports == null) return null;
        for (ImportBinding imported : scope.imports) {
            if (imported != null && imported.reference == node) {
                return imported.getResolvedImport();
            }
        }
        return null;
    }

    private static void accept(java.util.function.Consumer<SymbolOccurrence> consumer,
                               Binding binding, int start, int end) {
        if (binding != null && binding.isValidBinding() && start >= 0 && end >= start) {
            consumer.accept(new SymbolOccurrence(binding, start, end, false));
        }
    }

    private static void declare(java.util.function.Consumer<SymbolOccurrence> consumer,
                                Binding binding, int start, int end) {
        if (binding != null && binding.isValidBinding() && start >= 0 && end >= start) {
            consumer.accept(new SymbolOccurrence(binding, start, end, true));
        }
    }

    private static Binding validType(TypeBinding binding) {
        if (binding == null || !binding.isValidBinding()) return null;
        TypeBinding leaf = binding.leafComponentType();
        return leaf instanceof ReferenceBinding ? leaf : null;
    }

    private Optional<Location> definitionFor(Binding binding) {
        SymbolOccurrence[] declaration = new SymbolOccurrence[1];
        visitOccurrences(candidate -> {
            if (candidate.declaration && declaration[0] == null && (candidate.binding == binding
                    || sameBinding(candidate.binding, binding))) {
                declaration[0] = candidate;
            }
        });
        if (declaration[0] != null) {
            return Optional.of(location(declaration[0].start, declaration[0].end));
        }
        return externalDefinition(binding);
    }

    private Optional<Location> externalDefinition(Binding binding) {
        if (declarationLocator == null) return Optional.empty();
        String ownerJvm = ownerJvmName(binding);
        if (ownerJvm == null) return Optional.empty();
        return declarationLocator.locate(entry(ownerJvm), binding, sourceJarByBinaryJar);
    }

    private static String ownerJvmName(Binding binding) {
        if (binding instanceof TypeBinding type) {
            TypeBinding leaf = type.leafComponentType().erasure();
            return leaf instanceof ReferenceBinding reference ? new String(reference.constantPoolName()) : null;
        }
        if (binding instanceof MethodBinding method && method.declaringClass != null) {
            return new String(method.declaringClass.erasure().constantPoolName());
        }
        if (binding instanceof FieldBinding field && field.declaringClass != null) {
            return new String(field.declaringClass.erasure().constantPoolName());
        }
        return null;
    }

    private SymbolIdentity identityOf(Binding binding) {
        if (binding instanceof LocalVariableBinding local) {
            return new SymbolIdentity(null, new String(local.name), true, Optional.empty());
        }
        if (binding instanceof TypeBinding type) {
            TypeBinding leaf = type.leafComponentType().erasure();
            if (!(leaf instanceof ReferenceBinding reference)) return null;
            String ownerJvm = new String(reference.constantPoolName());
            Optional<String> origin = origin(ownerJvm);
            if (origin.isEmpty()) return null;
            String binary = ownerJvm.replace('/', '.');
            return new SymbolIdentity("T:" + origin.get() + "|" + binary,
                    new String(reference.sourceName()), false, origin);
        }
        if (binding instanceof MethodBinding method && method.declaringClass != null) {
            String ownerJvm = new String(method.declaringClass.erasure().constantPoolName());
            Optional<String> origin = origin(ownerJvm);
            if (origin.isEmpty()) return null;
            String name = new String(method.selector);
            String simple = method.isConstructor()
                    ? new String(method.declaringClass.sourceName()) : name;
            List<String> parameters = new ArrayList<>(method.parameters.length);
            for (TypeBinding parameter : method.parameters) {
                parameters.add(new String(parameter.erasure().readableName()));
            }
            String key = "M:" + origin.get() + "|" + ownerJvm.replace('/', '.')
                    + "#" + name + "(" + String.join(",", parameters) + ")";
            return new SymbolIdentity(key, simple, false, origin);
        }
        if (binding instanceof FieldBinding field && field.declaringClass != null) {
            String ownerJvm = new String(field.declaringClass.erasure().constantPoolName());
            Optional<String> origin = origin(ownerJvm);
            if (origin.isEmpty()) return null;
            String name = new String(field.name);
            String key = "F:" + origin.get() + "|" + ownerJvm.replace('/', '.') + "#" + name;
            return new SymbolIdentity(key, name, false, origin);
        }
        return null;
    }

    private Optional<String> origin(String ownerJvm) {
        if (uri != null && declares(ownerJvm)) return Optional.of(uri.toString());
        TypeEntry entry = entry(ownerJvm);
        if (entry != null && entry.resourceUri() != null && !entry.resourceUri().isBlank()) {
            return Optional.of(entry.resourceUri());
        }
        return Optional.empty();
    }

    private TypeEntry entry(String ownerJvm) {
        if (index == null) return null;
        return classpath.pick(index.getAll(ownerJvm), TypeEntry::sourceUri);
    }

    private boolean declares(String ownerJvm) {
        if (unit.types == null) return false;
        for (TypeDeclaration type : unit.types) {
            if (type.binding != null && ownerJvm.equals(new String(type.binding.constantPoolName()))) return true;
        }
        return false;
    }

    private Location location(int start, int end) {
        String documentUri = uri == null ? "" : uri.toString();
        return new Location(documentUri, new Range(
                EcjAnalysisEngine.positionAt(source, start),
                EcjAnalysisEngine.positionAt(source, end + 1)));
    }

    private static boolean sameBinding(Binding left, Binding right) {
        if (left == null || right == null) return false;
        try {
            return java.util.Arrays.equals(left.computeUniqueKey(), right.computeUniqueKey());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private int findName(char[] name, int start, int end) {
        if (name == null) return start;
        int found = source.indexOf(new String(name), Math.max(0, start));
        return found >= 0 && found <= end ? found : start;
    }

    private static int high(long position) {
        return (int) (position >>> 32);
    }

    private static int low(long position) {
        return (int) position;
    }

    private record SymbolOccurrence(Binding binding, int start, int end, boolean declaration) {}

    private record EcjResolvedSymbol(
            SymbolIdentity identity,
            Optional<Location> definition,
            Binding binding) implements ResolvedSymbol {}
}
