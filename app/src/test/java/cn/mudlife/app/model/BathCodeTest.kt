package cn.mudlife.app.model

import org.junit.Assert.*
import org.junit.Test

class BathCodeTest {
    @Test fun unknownPrefixNeverGetsInventedDefaultDigits() {
        assertNull(BathCode.complete("789", ""))
    }
    @Test fun savedPrefixPreservesLeadingZeros() {
        assertEquals("00123789", BathCode.complete("789", "00123"))
    }
    @Test fun fullServerCodeWinsOverOutdatedLocalPrefix() {
        assertEquals("98765432", BathCode.complete("98765432", "00123"))
    }
    @Test fun malformedResponsesCannotProduceCopyableCode() {
        for (s in listOf(null, "", "***", "12", "1234", "12 34 56")) assertNull(BathCode.complete(s, "00123"))
        assertNull(BathCode.complete("789", "１２３４５"))
    }
    @Test fun accountsAndProjectsHaveSeparateSavedPrefixes() {
        assertNotEquals(BathCode.storageKey("13000000000", "43"), BathCode.storageKey("13000000001", "43"))
        assertNotEquals(BathCode.storageKey("13000000000", "43"), BathCode.storageKey("13000000000", "44"))
        assertNull(BathCode.storageKey("", "43"))
    }
    @Test fun explicit13579PrefixIsValidAndMustNotBePurgedOnStartup() {
        assertTrue(BathCode.isPrefix("13579"))
        assertEquals("13579789", BathCode.complete("789", "13579"))
    }
}
