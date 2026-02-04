package com.ead.dispatch.update

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateCommandProviderTest {
    @Test
    fun `commands are generated for known sources`() {
        val provider = DefaultUpdateCommandProvider()

        assertEquals(
            "brew update && brew upgrade xtory",
            provider.commandFor(UpdateSource.HOMEBREW, "xtory"),
        )
        assertEquals(
            "scoop update && scoop update xtory",
            provider.commandFor(UpdateSource.SCOOP, "xtory"),
        )
        assertEquals(
            "sudo apt update && sudo apt install --only-upgrade xtory",
            provider.commandFor(UpdateSource.APT, "xtory"),
        )
    }
}
