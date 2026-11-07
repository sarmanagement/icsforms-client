/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.sarmanagement.icsforms.model.AppData;
import org.sarmanagement.icsforms.model.OperatorContext;
import org.sarmanagement.icsforms.model.Provenance;
import org.sarmanagement.icsforms.model.ResourceStatus;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers T-card provenance through the actual editor and persistence workflows. */
@ExtendWith(SwingTestLifecycleExtension.class)
@EnabledIfSystemProperty(named = "icsforms.desktopTests", matches = "true")
class TCardPanelProvenanceTest {
	@Test
	void allPersonnelCreationPathsAppendAttributedCheckInsAndPersistLocally() throws Exception {
		AppData data = new AppData();
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
		AppController controller = fixture.controller();
		controller.setOperatorContext(new OperatorContext("Incident Operator", "station-3"));
		TCardPanel panel = createPanel(controller);
		Instant generalCheckIn = Instant.parse("2026-11-07T16:00:00Z");
		Instant personnelCheckIn = Instant.parse("2026-11-07T16:15:00Z");
		Instant pickerCheckIn = Instant.parse("2026-11-07T16:30:00Z");

		runWithDialogs(panel, frame -> invokePanelMethod(panel, "addCardWithTypeChoice"),
				titleStep("Add T-Card", TCardPanelProvenanceTest::confirm),
				checkInStep(generalCheckIn));
		runWithDialogs(panel, frame -> invokePanelMethod(panel, "addPersonnelCard"), checkInStep(personnelCheckIn));
		AtomicReference<TCard> pickerCard = new AtomicReference<>();
		runWithDialogs(panel, frame -> pickerCard.set(
				panel.createPersonnelCardFromPicker("Picker Responder", "", "")), checkInStep(pickerCheckIn));
		runWithDialogs(panel, frame -> invokePanelMethod(panel, "addCardWithTypeChoice"),
				titleStep("Add T-Card", dialog -> {
					selectCardType(dialog, TCardType.HEADER);
					confirm(dialog);
				}),
				titleStep("Edit T-Card", TCardPanelProvenanceTest::confirm));

		List<TCard> cards = panelCards(panel);
		assertCheckIn(cardWithCheckInAt(cards, generalCheckIn), generalCheckIn, "Incident Operator", "station-3");
		assertCheckIn(cardWithCheckInAt(cards, personnelCheckIn), personnelCheckIn, "Incident Operator", "station-3");
		assertSame(cardWithCheckInAt(cards, pickerCheckIn), pickerCard.get());
		assertCheckIn(cardWithCheckInAt(cards, pickerCheckIn), pickerCheckIn, "Incident Operator", "station-3");
		assertTrue(cards.stream().filter(card -> card.getCardType() == TCardType.HEADER)
				.allMatch(card -> card.getCheckInOutEvents().isEmpty()));
		assertTrue(cards.stream().anyMatch(card -> card.getCardType() == TCardType.HEADER));

		pushToModel(panel);
		controller.save();
		controller.save();
		AppData restored = fixture.store().load(fixture.incidentId());
		for (Instant time : List.of(generalCheckIn, personnelCheckIn, pickerCheckIn)) {
			Provenance provenance = cardWithCheckInAt(restored.getTCards(), time)
					.getCheckInOutEvents().get(0).provenance();
			assertProvenance(provenance, "Incident Operator", "station-3");
		}
		assertTrue(restored.getTCards().stream().filter(card -> card.getCardType() == TCardType.HEADER)
				.allMatch(card -> card.getCheckInOutEvents().isEmpty()));
	}

	@Test
	void cancellationUnchangedAndUnrelatedEditsPreserveHistoryWhileCorrectionsAppend() throws Exception {
		Instant originalTime = Instant.parse("2026-11-07T16:00:00Z");
		Instant earlierEntry = Instant.parse("2026-11-07T16:01:00Z");
		Instant correctedTime = Instant.parse("2026-11-07T16:10:00Z");
		TCard card = personnelCard("Responder");
		card.setCheckInDateTime(LocalDateTime.ofInstant(originalTime, ZoneOffset.UTC));
		card.recordCheckIn(originalTime, Provenance.local("Prior Operator", null, earlierEntry));
		TCard legacyCard = personnelCard("Legacy Responder");
		TCard invalidatedCard = checkedInCard("Disappearing Responder", originalTime);
		AppData data = new AppData();
		data.setTCards(List.of(card, legacyCard, invalidatedCard));
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
		AppController controller = fixture.controller();
		controller.setOperatorContext(new OperatorContext("Current Operator", "station-7"));
		TCardPanel panel = createPanel(controller);

		runSelectedCardEdit(panel, "Responder", titleStep("Edit T-Card", dialog -> {
			setCheckInTime(dialog, correctedTime);
			cancel(dialog);
		}));
		assertEquals(1, card.getCheckInOutEvents().size());
		assertEquals(originalTime, card.getCheckInOutEvents().get(0).eventAt());

		runSelectedCardEdit(panel, "Responder", titleStep("Edit T-Card", dialog -> {
			selectStatus(dialog, "Assigned");
			confirm(dialog);
		}));
		runSelectedCardEdit(panel, "Responder", titleStep("Edit T-Card", TCardPanelProvenanceTest::confirm));
		assertEquals(1, cardNamed(panelCards(panel), "Responder").getCheckInOutEvents().size());

		runSelectedCardEdit(panel, "Responder", checkInStep(correctedTime));
		TCard corrected = cardNamed(panelCards(panel), "Responder");
		String originalResourceId = card.getResourceId();
		assertEquals(LocalDateTime.ofInstant(correctedTime, ZoneOffset.UTC), corrected.getCheckInDateTime());
		assertEquals(originalResourceId, corrected.getResourceId());
		assertEquals(2, corrected.getCheckInOutEvents().size());
		assertEquals(originalTime, corrected.getCheckInOutEvents().get(0).eventAt());
		assertEquals(correctedTime, corrected.getCheckInOutEvents().get(1).eventAt());
		assertProvenance(corrected.getCheckInOutEvents().get(1).provenance(), "Current Operator", "station-7");

		runSelectedCardEdit(panel, "Legacy Responder", titleStep("Edit T-Card", dialog -> {
			selectStatus(dialog, "Enroute");
			confirm(dialog);
		}));
		TCard unchangedLegacy = cardNamed(panelCards(panel), "Legacy Responder");
		assertNull(unchangedLegacy.getCheckInDateTime());
		assertTrue(unchangedLegacy.getCheckInOutEvents().isEmpty());

		Instant invalidCorrection = Instant.parse("2026-11-07T16:20:00Z");
		runSelectedCardEdit(panel, "Disappearing Responder", titleStep("Edit T-Card", dialog -> {
			setCheckInTime(dialog, invalidCorrection);
			removeCardFromPanel(panel, "Disappearing Responder");
			confirm(dialog);
		}));
		assertFalse(panelCards(panel).contains(invalidatedCard));
		assertEquals(1, invalidatedCard.getCheckInOutEvents().size());
		assertEquals(originalTime, invalidatedCard.getCheckInOutEvents().get(0).eventAt());

		pushToModel(panel);
		controller.save();
		AppData restored = fixture.store().load(fixture.incidentId());
		TCard restoredCorrection = cardNamed(restored.getTCards(), "Responder");
		assertEquals(2, restoredCorrection.getCheckInOutEvents().size());
		assertNull(restoredCorrection.getCheckInOutEvents().get(1).provenance().acceptedAt());
		TCard restoredLegacy = cardNamed(restored.getTCards(), "Legacy Responder");
		assertNull(restoredLegacy.getCheckInDateTime());
		assertTrue(restoredLegacy.getCheckInOutEvents().isEmpty());
		assertTrue(restored.getTCards().stream()
				.noneMatch(restoredCard -> "Disappearing Responder".equals(restoredCard.getPersonName())));
	}

	@Test
	void emptyOperatorContextLeavesUiCreatedCheckInUnattributed() throws Exception {
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(new AppData());
		TCardPanel panel = createPanel(fixture.controller());
		Instant checkIn = Instant.parse("2026-11-07T16:45:00Z");

		runWithDialogs(panel, frame -> invokePanelMethod(panel, "addPersonnelCard"), checkInStep(checkIn));

		TCard card = cardWithCheckInAt(panelCards(panel), checkIn);
		assertEquals(1, card.getCheckInOutEvents().size());
		assertEquals(checkIn, card.getCheckInOutEvents().get(0).eventAt());
		assertNull(card.getCheckInOutEvents().get(0).provenance());
	}

	@Test
	void relatedResourceEditorRecordsCheckInWithCurrentOperator() throws Exception {
		TCard person = personnelCard("Handler");
		TCard equipment = new TCard();
		equipment.setCardType(TCardType.EQUIPMENT);
		equipment.setResourceIdentifier("Canine K-9");
		equipment.setHandlerName("Handler");
		AppData data = new AppData();
		data.setTCards(List.of(person, equipment));
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
		fixture.controller().setOperatorContext(new OperatorContext("Related Editor", "station-9"));
		TCardPanel panel = createPanel(fixture.controller());
		AtomicReference<JDialog> parentDialog = new AtomicReference<>();
		Instant checkIn = Instant.parse("2026-11-07T17:00:00Z");

		runWithDialogs(panel,
				frame -> invokePrivate(panel, "openEditDialog",
						new Class<?>[]{TCard.class, Component.class}, equipment, frame),
				new DialogStep("Edit T-Card", dialog -> !(dialog.getOwner() instanceof JDialog), dialog -> {
					parentDialog.set(dialog);
					clickButton(dialog, "Open…");
				}),
				new DialogStep("Edit T-Card", dialog -> dialog != parentDialog.get(),
						dialog -> setCheckInTimeAndConfirm(dialog, checkIn)),
				new DialogStep("Edit T-Card", dialog -> dialog == parentDialog.get(),
						TCardPanelProvenanceTest::confirm));

		TCard editedPerson = cardNamed(panelCards(panel), "Handler");
		assertCheckIn(editedPerson, checkIn, "Related Editor", "station-9");
	}

	@Test
	void onlyDemobilizationRecordsCheckOutAndRemovalDoesNot() throws Exception {
		Instant checkIn = Instant.parse("2026-11-07T17:15:00Z");
		TCard card = checkedInCard("Demobilizing Responder", checkIn);
		TCard removedCard = checkedInCard("Removed Responder", checkIn);
		AppData data = new AppData();
		data.setTCards(List.of(card, removedCard));
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
		AppController controller = fixture.controller();
		controller.setOperatorContext(new OperatorContext("Demobilization Officer", "station-12"));
		TCardPanel panel = createPanel(controller);

		runSelectedCardEdit(panel, "Demobilizing Responder", titleStep("Edit T-Card", dialog -> {
			selectStatus(dialog, ResourceStatus.DEMOBILIZED.getLabel());
			cancel(dialog);
		}));
		assertEquals(1, cardNamed(panelCards(panel), "Demobilizing Responder").getCheckInOutEvents().size());

		runSelectedCardEdit(panel, "Demobilizing Responder", titleStep("Edit T-Card", dialog -> {
			selectStatus(dialog, "Assigned");
			confirm(dialog);
		}));
		assertEquals(1, cardNamed(panelCards(panel), "Demobilizing Responder").getCheckInOutEvents().size());

		runSelectedCardEdit(panel, "Demobilizing Responder", titleStep("Edit T-Card", dialog -> {
			selectStatus(dialog, ResourceStatus.DEMOBILIZED.getLabel());
			confirm(dialog);
		}));
		TCard demobilized = cardNamed(panelCards(panel), "Demobilizing Responder");
		assertEquals(ResourceStatus.DEMOBILIZED.getLabel(), demobilized.getStatus());
		assertEquals(2, demobilized.getCheckInOutEvents().size());
		assertEquals(TCard.CheckInOutAction.CHECK_OUT, demobilized.getCheckInOutEvents().get(1).action());
		assertNotNull(demobilized.getCheckInOutEvents().get(1).eventAt());
		assertProvenance(demobilized.getCheckInOutEvents().get(1).provenance(),
				"Demobilization Officer", "station-12");

		runSelectedCardEdit(panel, "Demobilizing Responder",
				titleStep("Edit T-Card", TCardPanelProvenanceTest::confirm));
		assertEquals(2, cardNamed(panelCards(panel), "Demobilizing Responder").getCheckInOutEvents().size());
		runSelectedCardAction(panel, "Removed Responder", "removeSelectedCard", titleStep("Remove Resource",
				dialog -> clickButton(dialog, "Yes")));
		assertFalse(panelCards(panel).contains(removedCard));
		assertEquals(1, removedCard.getCheckInOutEvents().size());
		assertTrue(removedCard.getCheckInOutEvents().stream()
				.noneMatch(event -> event.action() == TCard.CheckInOutAction.CHECK_OUT));

		pushToModel(panel);
		controller.save();
		AppData restored = fixture.store().load(fixture.incidentId());
		TCard restoredDemobilized = cardNamed(restored.getTCards(), "Demobilizing Responder");
		assertEquals(2, restoredDemobilized.getCheckInOutEvents().size());
		assertEquals(TCard.CheckInOutAction.CHECK_OUT,
				restoredDemobilized.getCheckInOutEvents().get(1).action());
		assertNull(restoredDemobilized.getCheckInOutEvents().get(1).provenance().acceptedAt());
	}

	@Test
	void duplicateMergeKeepsAOnlyAndBOnlyHistoriesAndPersistsThem() throws Exception {
		Instant aTime = Instant.parse("2026-11-07T17:30:00Z");
		Instant bTime = Instant.parse("2026-11-07T17:45:00Z");
		TCard aWithHistory = personnelCard("A-only Responder");
		aWithHistory.recordCheckIn(aTime, Provenance.local("Original A", "station-a", aTime.plusSeconds(1)));
		aWithHistory.recordCheckOut(aTime.plusSeconds(30), null);
		TCard aWithoutHistory = personnelCard("A-only Responder");
		TCard bWithoutHistory = personnelCard("B-only Responder");
		TCard bWithHistory = personnelCard("B-only Responder");
		bWithHistory.recordCheckIn(bTime, null);
		bWithHistory.recordCheckOut(bTime.plusSeconds(30),
				Provenance.local("Original B", "station-b", bTime.plusSeconds(1)));
		List<TCard.CheckInOutEvent> expectedAHistory = List.copyOf(aWithHistory.getCheckInOutEvents());
		List<TCard.CheckInOutEvent> expectedBHistory = List.copyOf(bWithHistory.getCheckInOutEvents());
		AppData data = new AppData();
		data.setTCards(List.of(aWithHistory, aWithoutHistory, bWithoutHistory, bWithHistory));
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
		TCardPanel panel = createPanel(fixture.controller());

		runDuplicateMerges(panel, "Merge", "A-only Responder", "B-only Responder");

		assertEquals(expectedAHistory, cardNamed(panelCards(panel), "A-only Responder").getCheckInOutEvents());
		assertEquals(expectedBHistory, cardNamed(panelCards(panel), "B-only Responder").getCheckInOutEvents());
		pushToModel(panel);
		fixture.controller().save();
		fixture.controller().save();
		AppData restored = fixture.store().load(fixture.incidentId());
		assertEquals(expectedAHistory, cardNamed(restored.getTCards(), "A-only Responder").getCheckInOutEvents());
		assertEquals(expectedBHistory, cardNamed(restored.getTCards(), "B-only Responder").getCheckInOutEvents());
		for (TCard card : restored.getTCards()) {
			for (TCard.CheckInOutEvent event : card.getCheckInOutEvents()) {
				if (event.provenance() != null) {
					assertNull(event.provenance().acceptedAt());
				}
			}
		}
	}

	@Test
	void duplicateMergeDeduplicatesSharedEventsButRetainsDistinctSameTimeEvents() throws Exception {
		Instant eventAt = Instant.parse("2026-11-07T18:00:00Z");
		TCard.CheckInOutEvent sharedLegacy = new TCard.CheckInOutEvent(
				TCard.CheckInOutAction.CHECK_IN, eventAt, null, null);
		TCard.CheckInOutEvent sharedIdentified = new TCard.CheckInOutEvent(
				TCard.CheckInOutAction.CHECK_OUT, eventAt.plusSeconds(5), null);
		TCard cardA = personnelCard("Shared History Responder");
		cardA.setPhoneNumber("A phone");
		cardA.recordCheckOut(eventAt.plusSeconds(10),
				Provenance.local("Operator A", "station-a", eventAt.plusSeconds(20)));
		cardA.getCheckInOutEvents().add(sharedLegacy);
		cardA.getCheckInOutEvents().add(sharedIdentified);
		TCard cardB = personnelCard("Shared History Responder");
		cardB.setPhoneNumber("B phone");
		cardB.getCheckInOutEvents().add(sharedIdentified);
		cardB.getCheckInOutEvents().add(sharedLegacy);
		cardB.recordCheckIn(eventAt, null);
		TCard.CheckInOutEvent distinctSameTime = cardB.getCheckInOutEvents().get(2);
		TCard.CheckInOutEvent aEvent = cardA.getCheckInOutEvents().get(0);
		List<TCard.CheckInOutEvent> expected = List.of(aEvent, sharedLegacy, sharedIdentified, distinctSameTime);
		AppData data = new AppData();
		data.setTCards(List.of(cardA, cardB));
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
		TCardPanel panel = createPanel(fixture.controller());

		runDuplicateMerges(panel, "Merge", "Shared History Responder");

		TCard merged = cardNamed(panelCards(panel), "Shared History Responder");
		assertEquals(expected, merged.getCheckInOutEvents());
		assertEquals("A phone", merged.getPhoneNumber());
		assertNotEquals(sharedIdentified.eventId(), distinctSameTime.eventId());
		assertNull(sharedLegacy.eventId());
		assertNull(sharedLegacy.provenance());
		assertNull(distinctSameTime.provenance());
		assertEquals("Operator A", merged.getCheckInOutEvents().get(0).provenance().operatorName());
		pushToModel(panel);
		fixture.controller().save();
		AppData restored = fixture.store().load(fixture.incidentId());
		TCard restoredCard = cardNamed(restored.getTCards(), "Shared History Responder");
		assertEquals(expected, restoredCard.getCheckInOutEvents());
		assertEquals(4, restoredCard.getCheckInOutEvents().size());
		assertTrue(restoredCard.getCheckInOutEvents().stream()
				.filter(event -> event.provenance() != null)
				.allMatch(event -> event.provenance().acceptedAt() == null));
	}

	@Test
	void cancellingDuplicateMergeLeavesBothCardsAndHistoriesUnchanged() throws Exception {
		Instant eventAt = Instant.parse("2026-11-07T18:15:00Z");
		TCard cardA = personnelCard("Cancelled Merge Responder");
		cardA.recordCheckIn(eventAt, Provenance.local("A Operator", "station-a", eventAt.plusSeconds(1)));
		TCard cardB = personnelCard("Cancelled Merge Responder");
		cardB.recordCheckOut(eventAt.plusSeconds(10),
				Provenance.local("B Operator", "station-b", eventAt.plusSeconds(11)));
		List<TCard.CheckInOutEvent> aHistory = List.copyOf(cardA.getCheckInOutEvents());
		List<TCard.CheckInOutEvent> bHistory = List.copyOf(cardB.getCheckInOutEvents());
		AppData data = new AppData();
		data.setTCards(List.of(cardA, cardB));
		IncidentStoreTestFixtures.Fixture fixture = IncidentStoreTestFixtures.create(data);
		TCardPanel panel = createPanel(fixture.controller());

		runDuplicateMerges(panel, "Cancel all", "Cancelled Merge Responder");

		assertEquals(2, panelCards(panel).size());
		assertEquals(aHistory, cardNamed(panelCards(panel), "Cancelled Merge Responder").getCheckInOutEvents());
		assertEquals(bHistory, panelCards(panel).get(1).getCheckInOutEvents());
		assertEquals(2, fixture.controller().getData().getTCards().size());
		assertEquals(aHistory, fixture.controller().getData().getTCards().get(0).getCheckInOutEvents());
		assertEquals(bHistory, fixture.controller().getData().getTCards().get(1).getCheckInOutEvents());
	}

	/**
	 * Creates a panel on the Swing event thread and loads its initial cards.
	 *
	 * @param controller active incident controller.
	 * @return loaded T-card panel.
	 * @throws Exception if the Swing event thread cannot be accessed.
	 */
	private static TCardPanel createPanel(AppController controller) throws Exception {
		AtomicReference<TCardPanel> panel = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			TCardPanel created = new TCardPanel(controller);
			created.refreshFromModel();
			panel.set(created);
		});
		return panel.get();
	}

	/**
	 * Runs a panel action while automated interactions complete its modal dialogs.
	 *
	 * @param panel panel whose real user action is invoked.
	 * @param action user action to invoke on the Swing event thread.
	 * @param steps expected modal dialogs and their interactions.
	 * @throws Exception if the event thread, dialog action, or expected dialog fails.
	 */
	private static void runWithDialogs(TCardPanel panel, Consumer<JFrame> action, DialogStep... steps)
			throws Exception {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			JFrame frame = new JFrame("T-card provenance test");
			frame.add(panel);
			frame.setSize(700, 600);
			AtomicInteger stepIndex = new AtomicInteger();
			long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
			Timer timer = new Timer(75, event -> {
				int index = stepIndex.get();
				if (index >= steps.length) {
					((Timer) event.getSource()).stop();
					return;
				}
				if (System.nanoTime() > deadline) {
					failure.set(new AssertionError("Timed out waiting for dialog " + steps[index].title()));
					disposeDialogs();
					((Timer) event.getSource()).stop();
					return;
				}
				for (Window window : Window.getWindows()) {
					if (window instanceof JDialog dialog && dialog.isVisible()
							&& steps[index].matches(dialog)) {
						try {
							stepIndex.incrementAndGet();
							steps[index].action().accept(dialog);
						} catch (Throwable throwable) {
							failure.set(throwable);
							disposeDialogs();
							((Timer) event.getSource()).stop();
						}
						return;
					}
				}
			});
			timer.setCoalesce(false);
			frame.setVisible(true);
			timer.start();
			try {
				action.accept(frame);
			} catch (Throwable throwable) {
				failure.compareAndSet(null, throwable);
			} finally {
				timer.stop();
				frame.dispose();
			}
			if (failure.get() == null && stepIndex.get() != steps.length) {
				failure.set(new AssertionError("Expected " + steps.length + " dialog interactions, completed "
						+ stepIndex.get()));
			}
		});
		if (failure.get() != null) {
			throw new AssertionError("T-card UI workflow failed", failure.get());
		}
	}

	/**
	 * Runs the duplicate-merge action and resolves its merge and summary dialogs.
	 *
	 * @param panel T-card panel whose duplicate-merge workflow is invoked.
	 * @param choice merge-dialog button text.
	 * @param personNames names of duplicate pairs expected in scan order.
	 * @throws Exception if a displayed dialog interaction fails.
	 */
	private static void runDuplicateMerges(TCardPanel panel, String choice, String... personNames) throws Exception {
		DialogStep[] steps = new DialogStep[personNames.length + 1];
		for (int index = 0; index < personNames.length; index++) {
			steps[index] = titleStep("Merge duplicate T-cards: " + personNames[index],
					dialog -> clickButton(dialog, choice));
		}
		steps[steps.length - 1] = titleStep("Merge Duplicates", TCardPanelProvenanceTest::confirm);
		runWithDialogs(panel, frame -> invokePanelMethod(panel, "mergeDuplicates"), steps);
	}

	/**
	 * Drives the selected-card edit action with a selected table row.
	 *
	 * @param panel T-card panel to edit.
	 * @param row model row selected before invoking the editor.
	 * @param steps modal interactions for the editor.
	 * @throws Exception if the UI workflow fails.
	 */
	private static void runSelectedCardEdit(TCardPanel panel, String personName, DialogStep... steps) throws Exception {
		runSelectedCardAction(panel, personName, "editSelectedCard", steps);
	}

	/**
	 * Runs a selected-card action with the requested card selected in the table.
	 *
	 * @param panel T-card panel.
	 * @param personName selected personnel card's name.
	 * @param methodName action method invoked on the panel.
	 * @param steps expected modal interactions.
	 * @throws Exception if the UI workflow fails.
	 */
	private static void runSelectedCardAction(TCardPanel panel, String personName, String methodName,
			DialogStep... steps) throws Exception {
		runWithDialogs(panel, frame -> {
			JTableAccess.selectRow(panel, personName);
			invokePanelMethod(panel, methodName);
		}, steps);
	}

	/**
	 * Builds a dialog step that acts on a dialog with the given title.
	 *
	 * @param title expected dialog title.
	 * @param action dialog interaction.
	 * @return matching dialog step.
	 */
	private static DialogStep titleStep(String title, Consumer<JDialog> action) {
		return new DialogStep(title, dialog -> true, action);
	}

	/**
	 * Builds a check-in editor step with a selected time and acceptance.
	 *
	 * @param checkIn check-in instant.
	 * @return T-card editor interaction.
	 */
	private static DialogStep checkInStep(Instant checkIn) {
		return titleStep("Edit T-Card", dialog -> setCheckInTimeAndConfirm(dialog, checkIn));
	}

	/**
	 * Sets a check-in time, opens a resource editor and accepts the edit.
	 *
	 * @param dialog editor dialog.
	 * @param checkIn check-in instant.
	 */
	private static void setCheckInTimeAndConfirm(JDialog dialog, Instant checkIn) {
		setCheckInTime(dialog, checkIn);
		confirm(dialog);
	}

	/**
	 * Sets the editor's check-in time to a fixed instant.
	 *
	 * @param dialog T-card editor dialog.
	 * @param checkIn selected check-in instant.
	 */
	private static void setCheckInTime(JDialog dialog, Instant checkIn) {
		JSpinner spinner = findComponents(dialog, JSpinner.class).stream().filter(Component::isShowing)
				.filter(component -> "checkInDateTime".equals(component.getName())).findFirst().orElse(null);
		assertNotNull(spinner, "T-card editor must expose its check-in time");
		spinner.setValue(Date.from(checkIn));
		assertEquals(Date.from(checkIn), spinner.getValue());
	}

	/**
	 * Selects a card type in the card creation dialog.
	 *
	 * @param dialog creation dialog.
	 * @param cardType selected type.
	 */
	private static void selectCardType(JDialog dialog, TCardType cardType) {
		for (JComboBox<?> combo : findComponents(dialog, JComboBox.class)) {
			for (int index = 0; index < combo.getItemCount(); index++) {
				if (combo.getItemAt(index) instanceof TCardType) {
					combo.setSelectedItem(cardType);
					return;
				}
			}
		}
		throw new AssertionError("Card type selector is missing");
	}

	/**
	 * Selects a status value from a T-card editor.
	 *
	 * @param dialog card editor dialog.
	 * @param status requested status.
	 */
	private static void selectStatus(JDialog dialog, String status) {
		for (JComboBox<?> combo : findComponents(dialog, JComboBox.class)) {
			if (!combo.isShowing() || !"personnelStatus".equals(combo.getName())) {
				continue;
			}
			for (int index = 0; index < combo.getItemCount(); index++) {
				if (status.equals(combo.getItemAt(index))) {
					combo.setSelectedItem(status);
					assertEquals(status, combo.getSelectedItem());
					return;
				}
			}
		}
		throw new AssertionError("Status option is missing: " + status);
	}

	/**
	 * Accepts a modal dialog through its standard OK action.
	 *
	 * @param dialog dialog to accept.
	 */
	private static void confirm(JDialog dialog) {
		clickButton(dialog, "OK");
	}

	/**
	 * Cancels a modal dialog through its standard Cancel action.
	 *
	 * @param dialog dialog to cancel.
	 */
	private static void cancel(JDialog dialog) {
		clickButton(dialog, "Cancel");
	}

	/**
	 * Clicks a named button within a dialog's component tree.
	 *
	 * @param dialog dialog containing the action.
	 * @param text button text.
	 */
	private static void clickButton(JDialog dialog, String text) {
		for (JButton button : findComponents(dialog, JButton.class)) {
			if (text.equals(button.getText())) {
				button.doClick();
				return;
			}
		}
		throw new AssertionError("Dialog button is missing: " + text);
	}

	/**
	 * Collects matching components from a Swing container hierarchy.
	 *
	 * @param container component tree root.
	 * @param type requested component type.
	 * @param <T> component type.
	 * @return matching descendants, in component order.
	 */
	private static <T extends Component> List<T> findComponents(Container container, Class<T> type) {
		java.util.ArrayList<T> result = new java.util.ArrayList<>();
		for (Component child : container.getComponents()) {
			if (type.isInstance(child)) {
				result.add(type.cast(child));
			}
			if (child instanceof Container nested) {
				result.addAll(findComponents(nested, type));
			}
		}
		return result;
	}

	/**
	 * Invokes a private panel action used by the button/menu workflow.
	 *
	 * @param panel T-card panel.
	 * @param methodName action method.
	 */
	private static void invokePanelMethod(TCardPanel panel, String methodName) {
		invokePrivate(panel, methodName, new Class<?>[0]);
	}

	/**
	 * Invokes a panel method by its declared signature for end-to-end UI testing.
	 *
	 * @param target method owner.
	 * @param methodName method name.
	 * @param parameterTypes method parameter types.
	 * @param arguments method arguments.
	 * @return method result.
	 */
	private static Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes,
			Object... arguments) {
		try {
			Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
			method.setAccessible(true);
			return method.invoke(target, arguments);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("Cannot invoke " + methodName, exception);
		}
	}

	/**
	 * Creates a personnel T-card for workflow fixtures.
	 *
	 * @param name responder's name.
	 * @return personnel card.
	 */
	private static TCard personnelCard(String name) {
		TCard card = new TCard();
		card.setCardType(TCardType.PERSONNEL);
		card.setPersonName(name);
		return card;
	}

	/**
	 * Returns the panel's editable card snapshot.
	 *
	 * @param panel T-card panel.
	 * @return cards currently held by its table model.
	 */
	@SuppressWarnings("unchecked")
	private static List<TCard> panelCards(TCardPanel panel) {
		try {
			Field field = TCardPanel.class.getDeclaredField("tableModel");
			field.setAccessible(true);
			Object model = field.get(panel);
			Method getCards = model.getClass().getDeclaredMethod("getCards");
			getCards.setAccessible(true);
			return (List<TCard>) getCards.invoke(model);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("Cannot read T-card table model", exception);
		}
	}

	/**
	 * Flushes the panel's card snapshot to its incident model.
	 *
	 * @param panel T-card panel.
	 * @throws Exception if the Swing event thread cannot be accessed.
	 */
	private static void pushToModel(TCardPanel panel) throws Exception {
		SwingUtilities.invokeAndWait(panel::pushToModel);
	}

	/**
	 * Removes a card from the panel model while its edit dialog is open.
	 *
	 * @param panel T-card panel.
	 * @param personName card whose pending edit is being invalidated.
	 */
	private static void removeCardFromPanel(TCardPanel panel, String personName) {
		try {
			List<TCard> cards = panelCards(panel);
			int row = -1;
			for (int index = 0; index < cards.size(); index++) {
				if (personName.equals(cards.get(index).getPersonName())) {
					row = index;
					break;
				}
			}
			if (row < 0) {
				throw new AssertionError("Cannot remove T-card for " + personName);
			}
			Field field = TCardPanel.class.getDeclaredField("tableModel");
			field.setAccessible(true);
			Object model = field.get(panel);
			Method removeCard = model.getClass().getDeclaredMethod("removeCard", int.class);
			removeCard.setAccessible(true);
			removeCard.invoke(model, row);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("Cannot invalidate T-card edit", exception);
		}
	}

	/**
	 * Finds a card by its personnel name in the panel or stored incident.
	 *
	 * @param cards card collection.
	 * @param personName expected personnel name.
	 * @return matching card.
	 */
	private static TCard cardNamed(List<TCard> cards, String personName) {
		return cards.stream().filter(card -> personName.equals(card.getPersonName())).findFirst()
				.orElseThrow(() -> new AssertionError("Missing T-card for " + personName));
	}

	/**
	 * Finds the card containing a check-in at the requested effective time.
	 *
	 * @param cards card collection.
	 * @param eventAt effective check-in instant.
	 * @return matching card.
	 */
	private static TCard cardWithCheckInAt(List<TCard> cards, Instant eventAt) {
		return cards.stream().filter(card -> card.getCheckInOutEvents().stream()
				.anyMatch(event -> event.action() == TCard.CheckInOutAction.CHECK_IN && event.eventAt().equals(eventAt)))
				.findFirst().orElseThrow(() -> new AssertionError("Missing check-in event at " + eventAt));
	}

	/**
	 * Creates a personnel card with one historical check-in event.
	 *
	 * @param name responder's name.
	 * @param checkIn effective check-in instant.
	 * @return checked-in personnel card.
	 */
	private static TCard checkedInCard(String name, Instant checkIn) {
		TCard card = personnelCard(name);
		card.setStatus("Available");
		card.setCheckInDateTime(LocalDateTime.ofInstant(checkIn, ZoneOffset.UTC));
		card.recordCheckIn(checkIn, Provenance.local("Prior Operator", null, checkIn.plusSeconds(5)));
		return card;
	}

	/**
	 * Verifies one event and its device/operator attribution.
	 *
	 * @param card T-card whose event is inspected.
	 * @param eventAt effective event timestamp.
	 * @param operatorName current operator.
	 * @param stationId current station.
	 */
	private static void assertCheckIn(TCard card, Instant eventAt, String operatorName, String stationId) {
		assertEquals(1, card.getCheckInOutEvents().size());
		TCard.CheckInOutEvent event = card.getCheckInOutEvents().get(0);
		assertEquals(TCard.CheckInOutAction.CHECK_IN, event.action());
		assertEquals(eventAt, event.eventAt());
		assertProvenance(event.provenance(), operatorName, stationId);
	}

	/**
	 * Verifies provenance fields on a locally recorded event.
	 *
	 * @param provenance event attribution.
	 * @param operatorName expected operator.
	 * @param stationId expected station.
	 */
	private static void assertProvenance(Provenance provenance, String operatorName, String stationId) {
		assertNotNull(provenance);
		assertEquals(operatorName, provenance.operatorName());
		assertEquals(stationId, provenance.stationId());
		assertNotNull(provenance.enteredAt());
		assertNull(provenance.acceptedAt());
	}

	/**
	 * Disposes visible modal windows after an automated interaction fails.
	 */
	private static void disposeDialogs() {
		for (Window window : Window.getWindows()) {
			if (window instanceof JDialog && window.isVisible()) {
				window.dispose();
			}
		}
	}

	/**
	 * Defines one expected modal dialog and its interaction.
	 *
	 * @param title expected title.
	 * @param predicate additional dialog-selection rule.
	 * @param action dialog action.
	 */
	private record DialogStep(String title, Predicate<JDialog> predicate, Consumer<JDialog> action) {
		/**
		 * Returns whether a visible dialog matches this interaction step.
		 *
		 * @param dialog candidate dialog.
		 * @return true when title and owner predicate match.
		 */
		private boolean matches(JDialog dialog) {
			return title.equals(dialog.getTitle()) && predicate.test(dialog);
		}
	}

	/** Selects a table row before a user edit action. */
	private static final class JTableAccess {
		/** Prevents test utility instantiation. */
		private JTableAccess() {
		}

		/**
		 * Selects a row in the panel's resource table.
		 *
		 * @param panel card panel.
		 * @param row selected model row.
		 */
		private static void selectRow(TCardPanel panel, String personName) {
			try {
				Field field = TCardPanel.class.getDeclaredField("table");
				field.setAccessible(true);
				javax.swing.JTable table = (javax.swing.JTable) field.get(panel);
				List<TCard> cards = panelCards(panel);
				int modelRow = -1;
				for (int index = 0; index < cards.size(); index++) {
					if (personName.equals(cards.get(index).getPersonName())) {
						modelRow = index;
						break;
					}
				}
				if (modelRow < 0) {
					throw new AssertionError("Cannot select T-card for " + personName);
				}
				int viewRow = table.convertRowIndexToView(modelRow);
				table.setRowSelectionInterval(viewRow, viewRow);
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError("Cannot select T-card row", exception);
			}
		}
	}
}
