package io.github.darkryh.dispatch.update

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

    @Test
    fun `orders prerelease qualifiers with natural numbering`() {
        // Regression: qualifiers used to be discarded, so beta02 was "not newer" than beta01
        // and Dispatch's own release lineage could never self-update between betas.
        assertTrue(VersionComparator.isNewer("1.0.0-beta02", "1.0.0-beta01"))
        assertTrue(VersionComparator.compare("1.0.0-beta01", "1.0.0-beta02") < 0)
        assertTrue(VersionComparator.isNewer("1.0.0-beta10", "1.0.0-beta2"))
        assertEquals(0, VersionComparator.compare("1.0.0-beta02", "1.0.0-beta02"))
    }

    @Test
    fun `release is newer than any prerelease of the same core`() {
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.0-rc1"))
        assertTrue(VersionComparator.compare("1.0.0-rc1", "1.0.0") < 0)
    }

    @Test
    fun `orders prerelease stages alphabetically`() {
        assertTrue(VersionComparator.isNewer("1.0.0-beta01", "1.0.0-alpha05"))
        assertTrue(VersionComparator.isNewer("1.0.0-rc1", "1.0.0-beta10"))
    }

    @Test
    fun `core segments still dominate prerelease qualifiers`() {
        assertTrue(VersionComparator.isNewer("1.0.1-alpha01", "1.0.0"))
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0-beta99"))
    }

    @Test
    fun `build metadata is ignored`() {
        assertEquals(0, VersionComparator.compare("1.0.0+build7", "1.0.0"))
        assertTrue(VersionComparator.isNewer("1.0.0-beta02+build7", "1.0.0-beta01"))
    }

    @Test
    fun `extended qualifier is newer than its prefix`() {
        assertTrue(VersionComparator.isNewer("1.0.0-beta01-SNAPSHOT", "1.0.0-beta01"))
    }
}
