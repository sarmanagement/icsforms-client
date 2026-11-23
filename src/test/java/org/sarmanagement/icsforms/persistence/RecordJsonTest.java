/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.sarmanagement.icsforms.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

/** Covers semantic comparison of record JSON values. */
class RecordJsonTest {
	@Test
	void nonFiniteNumbersCompareWithoutDecimalConversion() {
		assertTrue(RecordJson.same(DoubleNode.valueOf(Double.NaN), DoubleNode.valueOf(Double.NaN)));
		assertTrue(RecordJson.same(DoubleNode.valueOf(Double.NaN), TextNode.valueOf("NaN")));
		assertTrue(RecordJson.same(DoubleNode.valueOf(Double.POSITIVE_INFINITY),
				DoubleNode.valueOf(Double.POSITIVE_INFINITY)));
		assertFalse(RecordJson.same(DoubleNode.valueOf(Double.NaN), DoubleNode.valueOf(1.0)));
		assertFalse(RecordJson.same(DoubleNode.valueOf(Double.NaN), IntNode.valueOf(0)));
		assertFalse(RecordJson.same(DoubleNode.valueOf(Double.POSITIVE_INFINITY),
				DoubleNode.valueOf(Double.NEGATIVE_INFINITY)));
	}

	@Test
	void finiteNumbersStillCompareAcrossNodeTypes() {
		assertTrue(RecordJson.same(IntNode.valueOf(2), DoubleNode.valueOf(2.0)));
		assertFalse(RecordJson.same(IntNode.valueOf(2), DoubleNode.valueOf(2.5)));
		assertFalse(RecordJson.same(IntNode.valueOf(2), TextNode.valueOf("2")));
	}
}
