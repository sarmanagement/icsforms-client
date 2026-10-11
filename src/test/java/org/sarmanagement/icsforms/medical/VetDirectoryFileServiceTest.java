/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.medical;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sarmanagement.icsforms.model.VetDirectory;
import org.sarmanagement.icsforms.model.VetFacility;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies versioned local directory import, merge, and explicit export behavior.
 */
class VetDirectoryFileServiceTest {
	@TempDir
	Path tempDirectory;

	private final VetDirectoryFileService service = new VetDirectoryFileService();

	@Test
	void csvRoundTripPreservesQuotedUnicodeContactsAndUnknownAvailability() throws Exception {
		VetDirectory directory = new VetDirectory();
		VetFacility facility = new VetFacility();
		facility.setId("regional-vet-1");
		facility.setName("Clinique \"Étoile\"");
		facility.setTown("Rivière, Québec");
		facility.setAddress("1 Example Road");
		facility.setContactNumbers(List.of("+1 555 0100", "front desk, line 2"));
		facility.setAvailable24Hours(null);
		facility.setAvailabilityNotes("Call to confirm,\nnight hours are unverified.");
		facility.setCapabilities("Small-animal emergency");
		facility.setCoordinates(46.8, -71.2, "manual", "operator-confirmed");
		directory.getFacilities().add(facility);
		Path csv = tempDirectory.resolve("region.csv");

		assertTrue(service.save(csv, directory, false));
		VetDirectoryFileService.ImportResult importResult = service.importFiles(List.of(csv), new VetDirectory());
		VetDirectory imported = importResult.directory();

		assertEquals(1, imported.getFacilities().size(), importResult.errors().toString());
		VetFacility result = imported.getFacilities().get(0);
		assertEquals("Clinique \"Étoile\"", result.getName());
		assertEquals("Rivière, Québec", result.getTown());
		assertEquals(facility.getContactNumbers(), result.getContactNumbers());
		assertNull(result.getAvailable24Hours());
		assertEquals(facility.getAvailabilityNotes(), result.getAvailabilityNotes());
		assertEquals(46.8, result.getLatitude());
		assertEquals("region.csv", result.getSourceName());
		assertEquals("CSV", imported.getSources().get(0).getFormat());
	}

	@Test
	void jsonImportVersionAndMergePreserveConflictsInsteadOfOverwriting() throws Exception {
		VetDirectory first = directoryEntry("shared-id", "Fictional North Vet", "1 North Road", "+1 555 0101");
		VetDirectory second = directoryEntry("shared-id", "Fictional South Vet", "2 South Road", "+1 555 0102");
		Path north = tempDirectory.resolve("north.json");
		Path south = tempDirectory.resolve("south.json");
		service.save(north, first, false);
		service.save(south, second, false);

		VetDirectoryFileService.ImportResult merged = service.importFiles(List.of(north, south), new VetDirectory());

		assertEquals(2, merged.directory().getFacilities().size());
		assertEquals(1, merged.conflicts().size());
		assertTrue(merged.errors().isEmpty());
		assertFalse(merged.directory().getFacilities().get(0).getId()
				.equals(merged.directory().getFacilities().get(1).getId()));
		assertEquals("JSON", merged.directory().getSources().get(0).getFormat());
	}

	@Test
	void addressAndContactDedupIsCautiousAndMalformedRowsAreReported() throws Exception {
		VetDirectory first = directoryEntry("id-one", "Fictional Vet", "10 Main Street", "+1 555 0100");
		VetDirectory duplicate = directoryEntry("id-two", "Another Label", " 10   Main Street ", "+1 555 0100");
		Path csv = tempDirectory.resolve("partial.csv");
		service.save(csv, duplicate, false);
		String malformedRow = "\"id-bad\",\"Bad Vet\",\"Town\",\"Address\",\"+1 555\",\"sometimes\","
				+ "\"\",\"\",\"\",\"\",\"\"\r\n";
		Files.writeString(csv, Files.readString(csv) + malformedRow);

		VetDirectoryFileService.ImportResult result = service.importFiles(List.of(csv), first);

		assertEquals(1, result.directory().getFacilities().size(), result.errors().toString());
		assertEquals(1, result.duplicateCount());
		assertEquals(1, result.errors().size());
		assertEquals(1, first.getFacilities().size());
	}

	@Test
	void overwriteRequiresConfirmationAndCancelDoesNotWrite() throws Exception {
		VetDirectory directory = directoryEntry("id", "Fictional Vet", "1 Main Street", "+1 555 0100");
		Path json = tempDirectory.resolve("vet-directory.json");
		assertFalse(service.save(null, directory, true));
		assertTrue(service.save(json, directory, false));
		String original = Files.readString(json);

		assertThrows(FileAlreadyExistsException.class, () -> service.save(json, new VetDirectory(), false));
		assertEquals(original, Files.readString(json));
		assertTrue(service.save(json, new VetDirectory(), true));
		assertFalse(Files.readString(json).contains("Fictional Vet"));
		try (var files = Files.list(tempDirectory)) {
			assertEquals(1, files.count());
		}
	}

	/**
	 * Creates one fictitious source directory entry.
	 *
	 * @param id stable facility ID.
	 * @param name display name.
	 * @param address physical address.
	 * @param phone contact value.
	 * @return one-entry directory.
	 */
	private VetDirectory directoryEntry(String id, String name, String address, String phone) {
		VetFacility facility = new VetFacility();
		facility.setId(id);
		facility.setName(name);
		facility.setAddress(address);
		facility.setContactNumbers(List.of(phone));
		VetDirectory directory = new VetDirectory();
		directory.getFacilities().add(facility);
		return directory;
	}
}
