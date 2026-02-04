package com.ead.dispatch.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionComparatorTest {
    @Test
    fun `compares semver segments`() {
        assertEquals(0, VersionComparator.compare("1.2.3", "1.2.3"))
        assertTrue(VersionComparator.compare("1.2.4", "1.2.3") > 0)
        assertTrue(VersionComparator.compare("2.0.0", "1.9.9") > 0)
        assertTrue(VersionComparator.compare("1.10.0", "1.9.9") > 0)
    }

    @Test
    fun `ignores tag prefixes`() {
        assertTrue(VersionComparator.compare("v2.0.0", "1.9.9") > 0)
    }
}
