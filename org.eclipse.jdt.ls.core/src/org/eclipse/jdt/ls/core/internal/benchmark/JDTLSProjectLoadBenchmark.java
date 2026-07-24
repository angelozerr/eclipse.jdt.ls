/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.benchmark;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.JobHelpers;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.m2e.core.internal.IMavenToolbox;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.ClientCapabilities;

/**
 * Headless benchmark tool that reproduces the JDT.LS project loading pipeline
 * with timing instrumentation at each phase.
 * <p>
 * Usage: Run as Eclipse Application with:
 * <pre>
 *   -application org.eclipse.jdt.ls.core.JDTLSBenchmark
 *   [project-directory]   (default: C:/Users/AngeloZerr/git/quarkus)
 * </pre>
 * VM args: {@code -Xmx4g} recommended for large projects.
 */
@SuppressWarnings("restriction")
public class JDTLSProjectLoadBenchmark implements IApplication {

	private static final String DEFAULT_DIR = "C:/Users/AngeloZerr/git/quarkus"; //$NON-NLS-1$

	private final List<Timing> timings = new ArrayList<>();

	record Timing(String label, long ms) {}

	@Override
	public Object start(IApplicationContext context) throws Exception {
		String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		String projectDir = (args != null && args.length > 0) ? args[0] : DEFAULT_DIR;

		boolean useFastRootCheck = Boolean.parseBoolean(System.getProperty("jdt.ls.import.fastRootCheck", "true")); //$NON-NLS-1$ //$NON-NLS-2$
		boolean useReadCache = Boolean.getBoolean("m2e.readMavenProject.cache"); //$NON-NLS-1$
		boolean useBatchResolve = Boolean.getBoolean("m2e.batch.resolveDependencies"); //$NON-NLS-1$
		boolean skipSort = Boolean.getBoolean("m2e.skip.sortProjects"); //$NON-NLS-1$
		int maxIterations = Integer.getInteger("m2e.project.refresh.maxIterations", 10); //$NON-NLS-1$
		int readBatchSize = Integer.getInteger("m2e.batch.readMavenProjects.size", 0); //$NON-NLS-1$

		// When batch resolve is enabled, auto-enable the cache so Phase 2 benefits from batch results
		if (useBatchResolve && !useReadCache) {
			IMavenToolbox.enableCache();
			useReadCache = true;
		}

		System.out.println("=== JDT.LS Project Load Benchmark ==="); //$NON-NLS-1$
		System.out.println("Project directory: " + projectDir); //$NON-NLS-1$
		System.out.println("Fast root check: " + useFastRootCheck); //$NON-NLS-1$
		System.out.println("readMavenProject cache: " + useReadCache); //$NON-NLS-1$
		System.out.println("Batch resolve dependencies: " + useBatchResolve); //$NON-NLS-1$
		System.out.println("Skip sort projects: " + skipSort); //$NON-NLS-1$
		System.out.println("Max Phase 2 iterations: " + maxIterations); //$NON-NLS-1$
		System.out.println("Read batch size: " + (readBatchSize > 0 ? readBatchSize : "ALL (no splitting)")); //$NON-NLS-1$ //$NON-NLS-2$
		System.out.println();

		// Initialize ClientPreferences before anything else (normally set by LSP handshake)
		PreferenceManager prefManager = JavaLanguageServerPlugin.getPreferencesManager();
		prefManager.updateClientPrefences(new ClientCapabilities(), null);
		System.out.println("ClientPreferences initialized."); //$NON-NLS-1$

		IProgressMonitor monitor = new NullProgressMonitor();
		long totalStart = System.currentTimeMillis();

		// ── Phase 1: Platform initialization ──
		System.out.println("── Phase 1: Platform initialization ──"); //$NON-NLS-1$

		timed("Wait for Initialize After Load job", () -> {
			JobHelpers.waitForJobs(JavaLanguageServerPlugin.INITIALIZE_AFTER_JOB, new NullProgressMonitor());
		});

		// ── Phase 2: Pre-import setup ──
		System.out.println();
		System.out.println("── Phase 2: Pre-import setup ──"); //$NON-NLS-1$

		timed("Wait for RepositoryRegistryUpdateJob", () -> {
			JobHelpers.waitForRepositoryRegistryUpdateJob();
		});

		// ── Phase 3: Project import ──
		System.out.println();
		System.out.println("── Phase 3: Project import ──"); //$NON-NLS-1$

		Collection<IPath> rootPaths = Collections.singletonList(new Path(projectDir));
		ProjectsManager projectsManager = JavaLanguageServerPlugin.getProjectsManager();

		timed("Interrupt auto-build", () -> {
			try {
				ProjectsManager.interruptAutoBuild();
			} catch (CoreException e) {
				System.err.println("Failed to interrupt auto-build: " + e.getMessage()); //$NON-NLS-1$
			}
		});

		timed("initializeProjects", () -> {
			try {
				projectsManager.initializeProjects(rootPaths, monitor);
			} catch (CoreException e) {
				System.err.println("Failed to initialize projects: " + e.getMessage()); //$NON-NLS-1$
				e.printStackTrace();
			}
		});

		// ── Phase 4: Post-import ──
		System.out.println();
		System.out.println("── Phase 4: Post-import ──"); //$NON-NLS-1$

		timed("Re-enable auto-build", () -> {
			try {
				enableAutoBuilding();
			} catch (CoreException e) {
				System.err.println("Failed to enable auto-build: " + e.getMessage()); //$NON-NLS-1$
			}
		});

		IMavenToolbox.resetReadMavenProjectCounters();
		timed("Wait for build jobs", () -> {
			JobHelpers.waitForBuildJobs(600_000);
		});
		IMavenToolbox.printReadMavenProjectSummary();

		timed("Wait for JDT indexing", () -> {
			JobHelpers.waitUntilIndexesReady();
		});

		// ── Summary ──
		long totalMs = System.currentTimeMillis() - totalStart;
		printSummary(totalMs);

		// Disable auto-build before exiting to prevent shutdown crashes
		// (m2e's PlexusContainer gets disposed while MavenBuilder is still running)
		try {
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IWorkspaceDescription description = workspace.getDescription();
			description.setAutoBuilding(false);
			workspace.setDescription(description);
		} catch (CoreException e) {
			// ignore
		}

		return EXIT_OK;
	}

	@Override
	public void stop() {
		// nothing
	}

	private void timed(String label, Runnable action) {
		System.out.printf("[START] %s%n", label); //$NON-NLS-1$
		long start = System.currentTimeMillis();
		try {
			action.run();
		} catch (Exception e) {
			System.err.printf("[ERROR] %s: %s%n", label, e.getMessage()); //$NON-NLS-1$
		}
		long elapsed = System.currentTimeMillis() - start;
		System.out.printf("[DONE]  %s — %,d ms%n%n", label, elapsed); //$NON-NLS-1$
		timings.add(new Timing(label, elapsed));
	}

	private void printSummary(long totalMs) {
		System.out.println();
		System.out.println("═══════════════════════════════════════════════════"); //$NON-NLS-1$
		System.out.println("                    SUMMARY                       "); //$NON-NLS-1$
		System.out.println("═══════════════════════════════════════════════════"); //$NON-NLS-1$

		long sumMs = timings.stream().mapToLong(Timing::ms).sum();
		long maxLabelLen = timings.stream().mapToLong(t -> t.label().length()).max().orElse(20);
		String fmt = "  %-" + maxLabelLen + "s  %,8d ms  %5.1f%%%s%n"; //$NON-NLS-1$ //$NON-NLS-2$

		long maxMs = timings.stream().mapToLong(Timing::ms).max().orElse(0);
		for (Timing t : timings) {
			String marker = (t.ms() == maxMs && t.ms() > 1000) ? "  ← bottleneck" : ""; //$NON-NLS-1$ //$NON-NLS-2$
			double pct = sumMs > 0 ? 100.0 * t.ms() / sumMs : 0;
			System.out.printf(fmt, t.label(), t.ms(), pct, marker);
		}

		System.out.println("  " + "─".repeat((int) maxLabelLen + 30)); //$NON-NLS-1$ //$NON-NLS-2$
		System.out.printf("  %-" + maxLabelLen + "s  %,8d ms  100.0%%%n", "TOTAL (measured)", sumMs); //$NON-NLS-1$ //$NON-NLS-2$
		System.out.printf("  %-" + maxLabelLen + "s  %,8d ms%n", "TOTAL (wall clock)", totalMs); //$NON-NLS-1$ //$NON-NLS-2$
		System.out.println();
	}

	private static void enableAutoBuilding() throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();
		if (!description.isAutoBuilding()) {
			description.setAutoBuilding(true);
			workspace.setDescription(description);
		}
	}
}
