/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sarmanagement.icsforms.model.IapPhase;
import org.sarmanagement.icsforms.model.TCard;
import org.sarmanagement.icsforms.model.TCardType;

class RackHeaderTest {

	@Test
	void defaultHeadersHaveStableIdentityAcrossIndependentCreation() {
		List<TCard> first = new ArrayList<>();
		List<TCard> second = new ArrayList<>();
		AppController.ensureDefaultHeaderCards(first, IapPhase.PRE_OP);
		AppController.ensureDefaultHeaderCards(second, IapPhase.PRE_OP);
		assertEquals(first.stream().map(TCard::getId).toList(), second.stream().map(TCard::getId).toList());
	}

	@Test
	void rackShowsOneColumnPerLabelInCanonicalOrder() {
		List<TCard> cards = new ArrayList<>();
		for (String label : List.of("Out of Service", "Available", "Assigned", "ICP", "Available", "ICP")) {
			TCard header = new TCard();
			header.setCardType(TCardType.HEADER);
			header.setResourceIdentifier(label);
			cards.add(header);
		}
		assertEquals(List.of("ICP", "Available", "Assigned", "Out of Service"),
				TCardPanel.distinctRackHeaders(cards).stream().map(TCard::getDisplayLabel).toList());
	}

	@Test
	void standardColumnsPrecedeCustomisedOnesWhichKeepStoredOrder() {
		List<TCard> cards = new ArrayList<>();
		for (String label : List.of("Staging", "Out of Service", "Medical", "Available", "Staging", "ICP")) {
			TCard header = new TCard();
			header.setCardType(TCardType.HEADER);
			header.setResourceIdentifier(label);
			cards.add(header);
		}
		assertEquals(List.of("ICP", "Available", "Out of Service", "Staging", "Medical"),
				TCardPanel.distinctRackHeaders(cards).stream().map(TCard::getDisplayLabel).toList());
	}
}
