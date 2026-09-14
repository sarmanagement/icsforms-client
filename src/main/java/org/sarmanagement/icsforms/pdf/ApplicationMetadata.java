package org.sarmanagement.icsforms.pdf;

import org.sarmanagement.icsforms.App;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class ApplicationMetadata {
	private static final String DEFAULT_NAME = "icsforms-client";
	private static final String MAVEN_PROPERTIES = "/META-INF/maven/org.sarmanagement/icsforms-client/pom.properties";
	private static final ApplicationMetadata DETECTED = detectUncached();

	private final String name;
	private final String version;
	private final String commitPrefix;

	ApplicationMetadata(String name, String version, String commitPrefix) {
		this.name = normalize(name);
		this.version = normalize(version);
		this.commitPrefix = normalize(commitPrefix);
	}

	static ApplicationMetadata detect() {
		return DETECTED;
	}

	private static ApplicationMetadata detectUncached() {
		String name = firstNonBlank(
				packageValue(App.class.getPackage() == null ? null : App.class.getPackage().getImplementationTitle()),
				loadPomProperty("artifactId"), DEFAULT_NAME);
		String version = firstNonBlank(
				packageValue(App.class.getPackage() == null ? null : App.class.getPackage().getImplementationVersion()),
				loadPomProperty("version"), readPomVersion(), "");
		String commitPrefix = version.endsWith("-SNAPSHOT") ? resolveGitCommitPrefix() : "";
		return new ApplicationMetadata(name, version, commitPrefix);
	}

	String preparedWithValue() {
		String base = version.isBlank() ? name : name + " " + version;
		if (!version.endsWith("-SNAPSHOT") || commitPrefix.isBlank()) {
			return base;
		}
		return base + " " + commitPrefix;
	}

	private static String loadPomProperty(String key) {
		try (InputStream stream = ApplicationMetadata.class.getResourceAsStream(MAVEN_PROPERTIES)) {
			if (stream == null) {
				return "";
			}
			Properties properties = new Properties();
			properties.load(stream);
			return normalize(properties.getProperty(key));
		} catch (Exception e) {
			return "";
		}
	}

	private static String readPomVersion() {
		Path pomPath = Path.of("pom.xml");
		if (!Files.isRegularFile(pomPath)) {
			return "";
		}
		try (InputStream inputStream = Files.newInputStream(pomPath)) {
			DocumentBuilderFactory factory = secureDocumentBuilderFactory();
			Document document = factory.newDocumentBuilder().parse(inputStream);
			Element project = document.getDocumentElement();
			if (project == null || !"project".equals(project.getTagName())) {
				return "";
			}
			for (Node child = project.getFirstChild(); child != null; child = child.getNextSibling()) {
				if (child.getNodeType() != Node.ELEMENT_NODE) {
					continue;
				}
				if ("version".equals(child.getNodeName())) {
					return normalize(child.getTextContent());
				}
			}
			return "";
		} catch (Exception e) {
			return "";
		}
	}

	private static DocumentBuilderFactory secureDocumentBuilderFactory() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory;
	}

	private static String resolveGitCommitPrefix() {
		try {
			Path gitDir = findGitDirectory(Path.of("").toAbsolutePath());
			if (gitDir == null) {
				return "";
			}
			String head = normalize(Files.readString(gitDir.resolve("HEAD")));
			if (head.startsWith("ref:")) {
				String ref = normalize(head.substring(4));
				Path refPath = gitDir.resolve(ref);
				if (Files.isRegularFile(refPath)) {
					return abbreviateHash(Files.readString(refPath));
				}
				Path packedRefs = gitDir.resolve("packed-refs");
				if (Files.isRegularFile(packedRefs)) {
					for (String line : Files.readAllLines(packedRefs)) {
						if (line.startsWith("#") || line.startsWith("^")) {
							continue;
						}
						String[] parts = line.trim().split("\\s+");
						if (parts.length == 2 && ref.equals(parts[1])) {
							return abbreviateHash(parts[0]);
						}
					}
				}
				return "";
			}
			return abbreviateHash(head);
		} catch (Exception e) {
			return "";
		}
	}

	private static Path findGitDirectory(Path start) {
		Path current = start;
		while (current != null) {
			Path candidate = current.resolve(".git");
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
			current = current.getParent();
		}
		return null;
	}

	private static String abbreviateHash(String value) {
		String normalized = normalize(value);
		return normalized.length() <= 10 ? normalized : normalized.substring(0, 10);
	}

	private static String packageValue(String value) {
		return normalize(value);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			String normalized = normalize(value);
			if (!normalized.isBlank()) {
				return normalized;
			}
		}
		return "";
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
