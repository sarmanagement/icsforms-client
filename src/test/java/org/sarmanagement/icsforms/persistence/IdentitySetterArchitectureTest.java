/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.Identified;

/** Prevents unreviewed production code from mutating record identities through setters. */
class IdentitySetterArchitectureTest {
	private static final Path SOURCE_ROOT = Path.of("src/main/java");

	@Test
	void identitySetterCallSitesOutsideModelAndPersistenceRemainExplicitlyAllowlisted() throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
			List<Path> sources;
			try (var paths = Files.walk(SOURCE_ROOT)) {
				sources = paths.filter(path -> path.toString().endsWith(".java"))
						.filter(path -> !path.startsWith(SOURCE_ROOT.resolve("org/sarmanagement/icsforms/model")))
						.filter(path -> !path.startsWith(SOURCE_ROOT.resolve("org/sarmanagement/icsforms/persistence")))
						.toList();
			}
			var sourceObjects = files.getJavaFileObjectsFromPaths(sources);
			JavacTask task = (JavacTask) compiler.getTask(null, files, null,
					List.of("-proc:none", "-classpath", System.getProperty("java.class.path")), null, sourceObjects);
			Iterable<? extends CompilationUnitTree> units = task.parse();
			task.analyze();
			Trees trees = Trees.instance(task);
			Path absoluteSourceRoot = SOURCE_ROOT.toAbsolutePath().normalize();
			Map<String, Integer> actual = new HashMap<>();
			for (CompilationUnitTree unit : units) {
				Path source = Path.of(unit.getSourceFile().toUri());
				String relative = absoluteSourceRoot.relativize(source).toString()
						.replace('\\', '/').replaceFirst("^org/sarmanagement/icsforms/", "");
				new TreePathScanner<Void, Void>() {
					@Override
					public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
						Element element = trees.getElement(new TreePath(getCurrentPath(), invocation.getMethodSelect()));
						if (element instanceof ExecutableElement method && isIdentitySetter(method)) {
							String site = relative + ":" + invocation;
							actual.merge(site, 1, Integer::sum);
						}
						return super.visitMethodInvocation(invocation, unused);
					}
				}.scan(unit, null);
			}
			assertEquals(allowedIdentitySetterSites(), actual);
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

	/**
	 * Lists existing, reviewed UI/import identity assignments and copy-preserving editor setters.
	 *
	 * @return approved production call sites and their occurrence counts
	 */
	private static Map<String, Integer> allowedIdentitySetterSites() {
		List<String> sites = List.of(
				"medical/VetDirectoryFileService.java:imported.setId(conflict.existingId())",
				"medical/VetDirectoryFileService.java:facility.setId(id)",
				"medical/VetDirectoryFileService.java:incoming.setId(UUID.randomUUID().toString())",
				"medical/VetDirectoryFileService.java:incoming.setId(UUID.randomUUID().toString())",
				"medical/VetDirectoryFileService.java:copy.setId(source.getId())",
				"ui/IncidentContextPanel.java:location.setId(String.valueOf(locationsModel.getValueAt(row, 3)))",
				"ui/Ics201Panel.java:copy.setId(entry.getId())",
				"ui/Ics201Panel.java:copy.setId(entry.getId())",
				"ui/Ics201Panel.java:row.setId(entry.getId())",
				"ui/Ics201Panel.java:row.setId(entry.getId())",
				"ui/RadioChannelCsvDialogs.java:result.setFormId(imported.getFormId())",
				"ui/Ics206Panel.java:station.setId(recordId(aidModel, row))",
				"ui/Ics206Panel.java:service.setId(recordId(transportModel, row))",
				"ui/Ics206Panel.java:hospital.setId(recordId(hospitalModel, row))",
				"ui/OrganizationalChartPanel.java:copy.setId(entry.getId())",
				"ui/OrganizationalChartPanel.java:entry.setId(existing.get(index).getId())",
				"ui/SarTaskPanel.java:target.setEntryId(source.getEntryId())",
				"ui/TCardPanel.java:copy.setResourceId(src.getResourceId())",
				"ui/TCardPanel.java:stub.setResourceId(cardResourceId)",
				"ui/TCardPanel.java:stub.setResourceId(cardResourceId)",
				"ui/CanineMedicalPanel.java:service.setId(transportRecordId(row))");
		return sites.stream().collect(Collectors.toMap(site -> site, ignored -> 1, Integer::sum));
	}
}
