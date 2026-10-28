/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Angelo ZERR - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers.ondemand;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.ls.core.internal.EventNotification;
import org.eclipse.jdt.ls.core.internal.EventType;
import org.eclipse.jdt.ls.core.internal.IProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaClientConnection.JavaLanguageClient;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.handlers.DocumentLifeCycleHandler;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.m2e.core.embedder.IMaven;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Centralized manager for on-demand project import.
 *
 * <p>In on-demand mode, projects are not imported at startup. Instead,
 * this manager intercepts {@code textDocument/didOpen} notifications and
 * tries each registered importer until one handles the opened file.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>At startup, {@link #initialize} registers importers that support
 *       on-demand mode. No project scanning or import happens.</li>
 *   <li>On each {@code didOpen}, {@link DocumentLifeCycleHandler} calls
 *       {@link #scheduleOnDemandImport}. Each importer lazily creates its
 *       module index on first use, walks up from the file to find
 *       the nearest build file, and imports the module if needed.</li>
 *   <li>At shutdown, {@link #closeProjectsOnShutdown} closes all imported
 *       projects so the next session starts clean.</li>
 * </ol>
 *
 * <h3>Stale project cleanup</h3>
 * <p>If a previous session crashed without proper shutdown, projects from
 * that session remain in the workspace. {@link #closeStaleProjects} must be
 * called early in initialization to close them, preventing the Eclipse
 * framework from indexing/building stale projects.</p>
 */
public class OnDemandImportManager {

	/**
	 * Importers that support on-demand mode, initialized once at startup.
	 * Each importer lazily creates its module index on first use.
	 */
	private final List<IProjectImporter> importers = new ArrayList<>();

	private final ModuleScanner moduleScanner = new ModuleScanner();

	private static final List<IClassFileSourceStrategy> STRATEGIES = List.of(
			new ScannedModulesStrategy(),
			new SourceAttachmentStrategy(),
			new JarPathStrategy()
	);

	private final Set<String> importingUris = ConcurrentHashMap.newKeySet();

	private Collection<IPath> rootPaths;

	private volatile boolean active;

	/**
	 * Initializes on-demand mode with the available importers.
	 *
	 * <p>Called at startup in place of {@code initializeProjects()}.
	 * Collects importers that support on-demand mode and initializes them
	 * with the workspace root folder. No scanning or import happens here;
	 * that is deferred to the first {@code didOpen}.</p>
	 *
	 * @param rootPaths the workspace root paths
	 */
	public void initialize(Collection<IPath> rootPaths) {
		// Called only once from InitHandler; no need to clear importers.
		this.rootPaths = rootPaths;
		// ex: rootPaths = [/home/user/quarkus]
		for (IPath rootPath : rootPaths) {
			File rootFolder = rootPath.toFile();
			// ex: importers() returns [MavenProjectImporter, GradleProjectImporter, ...]
			for (IProjectImporter importer : ProjectsManager.importers()) {
				// Only keep importers that support on-demand (Maven, Gradle)
				// ex: MavenProjectImporter.supportsOnDemand() → true
				// ex: InvisibleProjectImporter.supportsOnDemand() → false
				if (importer.supportsOnDemand()) {
					importer.initialize(rootFolder);
					importers.add(importer);
				}
			}
		}
		active = true;
		// ex: "On-demand mode initialized: 2 importer(s) registered for 1 root path(s)"
		JavaLanguageServerPlugin.logInfo("On-demand mode initialized: "
				+ importers.size() + " importer(s) registered for "
				+ rootPaths.size() + " root path(s)");

		// Start background scan of all workspace modules for "Go to Definition" support
		for (IPath rootPath : rootPaths) {
			Job scanJob = Job.create("Scan workspace modules", monitor -> {
				try {
					moduleScanner.scan(rootPath.toFile().toPath());
				} catch (RuntimeException e) {
					JavaLanguageServerPlugin.logException("Failed to scan workspace modules", e);
				}
				return Status.OK_STATUS;
			});
			scanJob.setPriority(Job.LONG);
			scanJob.setSystem(true);
			scanJob.schedule();
		}
	}

	/**
	 * Returns whether on-demand mode is active.
	 */
	public boolean isActive() {
		return active;
	}

	// ── Source resolution ─────────────────────────────────────────────────

	/**
	 * Finds the workspace source file for the given class file without importing.
	 *
	 * <p>Used by workspace symbol search (Ctrl+T) to return source locations
	 * instead of decompiled class locations. The actual import happens later
	 * via {@code textDocument/didOpen} when the user navigates to the result.</p>
	 *
	 * @param classFile the class file to resolve
	 * @return the source file URI, or {@code null} if not found in workspace
	 */
	public String findSourceForClassFile(IClassFile classFile) {
		if (!active) {
			return null;
		}
		try {
			IPackageFragment pkg = (IPackageFragment) classFile.getParent();
			String pkgPath = pkg.getElementName().replace('.', '/');
			String cfName = classFile.getElementName();
			int dollar = cfName.indexOf('$');
			String sourceName = (dollar > 0 ? cfName.substring(0, dollar) : cfName.replace(".class", "")) + ".java";
			String relativePath = pkgPath.isEmpty() ? sourceName : pkgPath + "/" + sourceName;

			for (IClassFileSourceStrategy strategy : STRATEGIES) {
				Path sourceFile = strategy.findSource(classFile, relativePath, moduleScanner);
				if (sourceFile != null) {
					return sourceFile.toUri().toString();
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Failed to find source for class file", e);
		}
		return null;
	}

	// ── On-demand import ────────────────────────────────────────────────

	/**
	 * Schedules an asynchronous import of the module containing the given file URI.
	 *
	 * <p>Called from {@link DocumentLifeCycleHandler#didOpen} when a file's
	 * project is not yet imported. The file opens immediately in syntax mode
	 * (fake compilation unit). When the background import completes,
	 * {@code didOpen} is replayed so the file transitions to standard mode.</p>
	 *
	 * @param uri the file URI that was opened
	 * @param params the original didOpen params, replayed after import
	 */
	public void scheduleOnDemandImport(String uri, org.eclipse.lsp4j.DidOpenTextDocumentParams params) {
		if (!active) {
			return;
		}
		if (!importingUris.add(uri)) {
			return;
		}
		Job importJob = Job.create("Importing project for " + uri, monitor -> {
			try {
				if (tryOnDemandImport(uri, monitor)) {
					if (didOpenReplayer != null) {
						didOpenReplayer.accept(params);
					}
				}
			} finally {
				importingUris.remove(uri);
			}
			return Status.OK_STATUS;
		});
		importJob.setPriority(Job.BUILD);
		importJob.schedule();
	}

	private java.util.function.Consumer<org.eclipse.lsp4j.DidOpenTextDocumentParams> didOpenReplayer;

	/**
	 * Sets the callback that replays {@code didOpen} after a successful
	 * on-demand import. This transitions the file from syntax mode
	 * (fake compilation unit) to standard mode with full diagnostics.
	 */
	public void setDidOpenReplayer(java.util.function.Consumer<org.eclipse.lsp4j.DidOpenTextDocumentParams> replayer) {
		this.didOpenReplayer = replayer;
	}

	/**
	 * Synchronously imports the module containing the given file URI.
	 *
	 * @param uri the file URI that was opened
	 * @param monitor progress monitor
	 * @return {@code true} if a module was imported (or already open)
	 */
	public boolean tryOnDemandImport(String uri, IProgressMonitor monitor) {
		if (!active) {
			return false;
		}
		for (IProjectImporter importer : importers) {
			try {
				List<IProject> imported = importer.importOnDemand(uri, monitor);
				if (!imported.isEmpty()) {
					notifyProjectsImported(imported);
					return true;
				}
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("On-demand import failed for " + uri, e);
			}
		}
		IFile file = JDTUtils.findFile(uri);
		if (file != null && file.getProject().isOpen()) {
			return true;
		}
		return false;
	}


	private void notifyProjectsImported(List<IProject> projects) {
		JavaLanguageClient client = JavaLanguageServerPlugin.getProjectsManager().getConnection();
		if (client == null) {
			return;
		}
		// ex: projects = [quarkus-core] → projectUris = [file:///workspace/quarkus/core/]
		// The client (vscode-java) forwards this to vscode-java-dependency
		// which adds the projects to the Java Projects tree view
		List<URI> projectUris = projects.stream()
				.map(p -> ProjectUtils.getProjectRealFolder(p).toFile().toURI())
				.collect(Collectors.toList());
		EventNotification notification = new EventNotification()
				.withType(EventType.ProjectsImported)
				.withData(projectUris);
		client.sendEventNotification(notification);
	}

	// ── Project cleanup ─────────────────────────────────────────────────

	/**
	 * Closes stale projects left open by a crashed previous session.
	 *
	 * <p>Only closes projects whose location is under one of the workspace
	 * root paths. Projects outside the workspace (e.g. external libraries)
	 * are left untouched.</p>
	 *
	 * @param rootPaths the workspace root paths
	 * @param monitor progress monitor
	 */
	public void closeStaleProjects(Collection<IPath> rootPaths, IProgressMonitor monitor) {
		closeProjects(rootPaths, monitor, "On-demand startup cleanup");
	}

	/**
	 * Deletes m2e's {@code workspaceState.ser} so the project registry starts
	 * empty. Without this, m2e retains cached {@code MavenProjectFacade}
	 * entries for closed projects, causing dependencies to resolve as project
	 * references to closed projects instead of JARs from {@code .m2/repository}.
	 */
	private void deleteMavenWorkspaceState() {
		try {
			Bundle bundle = FrameworkUtil.getBundle(IMaven.class);
			if (bundle == null) {
				return;
			}
			IPath statePath = Platform.getStateLocation(bundle);
			File workspaceStateFile = new File(statePath.toFile(), "workspaceState.ser");
			if (workspaceStateFile.exists()) {
				if (workspaceStateFile.delete()) {
					JavaLanguageServerPlugin.logInfo("On-demand shutdown: deleted m2e workspaceState.ser");
				}
			}
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException("Failed to delete m2e workspaceState.ser", e);
		}
	}

	/**
	 * Closes all on-demand imported projects before workspace save on shutdown.
	 *
	 * <p>This prevents the next session from loading stale projects at startup.</p>
	 *
	 * @param monitor progress monitor
	 */
	public void closeProjectsOnShutdown(IProgressMonitor monitor) {
		if (!active || rootPaths == null) {
			return;
		}
		closeProjects(rootPaths, monitor, "On-demand shutdown cleanup");
		deleteMavenWorkspaceState();
	}

	private void closeProjects(Collection<IPath> rootPaths, IProgressMonitor monitor, String context) {
		for (IPath root : rootPaths) {
			Path workspacePath = root.toFile().toPath().normalize();
			closeProjectsUnder(workspacePath, monitor, context);
		}
	}

	/**
	 * Closes open projects, optionally filtering by workspace path.
	 */
	private void closeProjectsUnder(Path workspacePath, IProgressMonitor monitor, String context) {
		IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		int closed = 0;
		for (IProject project : allProjects) {
			// Skip already-closed projects
			if (!project.isOpen()) {
				continue;
			}
			// If workspacePath is set, only close projects under that path
			// ex: workspacePath = /home/user/quarkus, project at /home/user/quarkus/core → close
			// ex: workspacePath = /home/user/quarkus, project at /home/user/other → skip
			// If workspacePath is null (shutdown), close all projects
			if (workspacePath != null && project.getLocation() != null) {
				Path projectPath = project.getLocation().toFile().toPath().normalize();
				if (!projectPath.startsWith(workspacePath)) {
					continue;
				}
			}
			try {
				// Close, not delete: preserves JDT index cache for fast reopen next session
				project.close(monitor);
				closed++;
			} catch (CoreException e) {
				JavaLanguageServerPlugin.logException("Failed to close project " + project.getName(), e);
			}
		}
		if (closed > 0) {
			JavaLanguageServerPlugin.logInfo(context + ": closed " + closed + " project(s)");
		}
	}
}
