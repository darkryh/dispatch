package io.github.darkryh.dispatch.vt

import kotlin.test.Test

/** Prints a scenario's screen at several points through the run. Diagnostic aid, not an assertion. */
class ScreenDumpTest {
    @Test
    fun `dump the screen at checkpoints`() {
        val name = System.getProperty("dispatch.vt.dump")?.takeIf { it.isNotBlank() } ?: return
        val scenario = Corpus.scenarios().firstOrNull { it.name == name } ?: return
        val frames = scenario.frames()
        val screen = VtScreen(scenario.width, scenario.height)
        val parser = VtParser(screen)
        val checkpoints = listOf(0.25, 0.50, 0.75, 0.95).map { (frames.size * it).toInt() }
        frames.forEachIndexed { index, frame ->
            frame.chunks.forEach { parser.feed(it.text) }
            if (index in checkpoints) {
                println("--- $name @ frame $index/${frames.size} ---")
                for (row in 0 until scenario.height) {
                    val text = screen.rowText(row).trimEnd()
                    if (text.isNotBlank()) println("%2d |%s".format(row, text.take(88)))
                }
            }
        }
    }
}
