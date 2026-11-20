/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.Identified;

/** Prevents production code outside model and persistence from reassigning record identities. */
class IdentitySetterArchitectureTest {
	private static final Path SOURCE_ROOT = Path.of("src/main/java");

	@Test
	void identitySetterCallSitesOutsideModelAndPersistenceAreAvoided() throws IOException {
		assertEquals(List.of(), identitySetterCallSites(SOURCE_ROOT));
	}

	@Test
	void identitySetterRuleDetectsAnOutsideCallSite() throws IOException {
		Path sourceRoot = Files.createTempDirectory("identity-setter-architecture");
		Path source = sourceRoot.resolve("org/sarmanagement/icsforms/ui/ForbiddenIdentitySetter.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, """
				package org.sarmanagement.icsforms.ui;

				import org.sarmanagement.icsforms.model.Identified;

				final class ForbiddenIdentitySetter {
					void reassign(Identified record) {
						record.setId("replacement");
					}
				}
				""");
		try {
			List<String> calls = identitySetterCallSites(sourceRoot);
			assertEquals(1, calls.size());
			assertTrue(calls.get(0).contains("record.setId(\"replacement\")"));
		} finally {
			try (var paths = Files.walk(sourceRoot)) {
				for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
	}

	/**
	 * Finds production source calls to record identity setters outside the allowed packages.
	 *
	 * @param sourceRoot root containing Java source files.
	 * @return detected identity setter call sites.
	 * @throws IOException when source files cannot be read.
	 */
	private static List<String> identitySetterCallSites(Path sourceRoot) throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
			List<Path> sources;
			try (var paths = Files.walk(sourceRoot)) {
				sources = paths.filter(path -> path.toString().endsWith(".java"))
						.filter(path -> !path.startsWith(sourceRoot.resolve("org/sarmanagement/icsforms/model")))
						.filter(path -> !path.startsWith(sourceRoot.resolve("org/sarmanagement/icsforms/persistence")))
						.toList();
			}
			var sourceObjects = files.getJavaFileObjectsFromPaths(sources);
			JavacTask task = (JavacTask) compiler.getTask(null, files, null,
					List.of("-proc:none", "-classpath", System.getProperty("java.class.path")), null, sourceObjects);
			Iterable<? extends CompilationUnitTree> units = task.parse();
			task.analyze();
			Trees trees = Trees.instance(task);
			Path absoluteSourceRoot = sourceRoot.toAbsolutePath().normalize();
			List<String> actual = new java.util.ArrayList<>();
			for (CompilationUnitTree unit : units) {
				Path source = Path.of(unit.getSourceFile().toUri());
				String relative = absoluteSourceRoot.relativize(source).toString()
						.replace('\\', '/').replaceFirst("^org/sarmanagement/icsforms/", "");
				new TreePathScanner<Void, Void>() {
					@Override
					public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
						Element element = trees.getElement(new TreePath(getCurrentPath(), invocation.getMethodSelect()));
						if (element instanceof ExecutableElement method && isIdentitySetter(method)) {
							actual.add(relative + ":" + invocation);
						}
						return super.visitMethodInvocation(invocation, unused);
					}
				}.scan(unit, null);
			}
			return actual;
		}
	}

	/**
	 * Identifies an identity setter using the model's canonical identity registry.
	 *
	 * @param method resolved production method
	 * @return whether the method assigns a record identity
	 */
	private static boolean isIdentitySetter(ExecutableElement method) {
		String name = method.getSimpleName().toString();
		if (!name.startsWith("set") || name.length() <= 3) {
			return false;
		}
		TypeElement owner = (TypeElement) method.getEnclosingElement();
		String ownerName = owner.getQualifiedName().toString();
		Class<?> recordType = IncidentDocumentReader.RECORD_IDENTITIES.keySet().stream()
				.filter(type -> type.getCanonicalName().equals(ownerName)).findFirst().orElse(null);
		if (recordType == null) {
			return "setId".equals(name) && Identified.class.getCanonicalName().equals(ownerName);
		}
		if (!Identified.class.isAssignableFrom(recordType)) {
			return false;
		}
		String property = Character.toLowerCase(name.charAt(3)) + name.substring(4);
		return "setId".equals(name) && Identified.class.isAssignableFrom(recordType)
				|| IncidentDocumentReader.RECORD_IDENTITIES.get(recordType).contains(property);
	}

}
