package io.github.darkryh.dispatch.workspace

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DefaultWorkspaceWatcherTest {
    @Test
    fun `emits create event`() =
        runBlocking {
            val root = Files.createTempDirectory("dispatch-workspace")
            val watcher =
                DefaultWorkspaceWatcher(
                    WorkspaceWatchConfig(
                        root = root,
                        debounce = 0.milliseconds,
                    ),
                )
            watcher.start()
            try {
                val file = root.resolve("created.txt")
                Files.createFile(file)
                file.writeText("hello")

                val event =
                    withTimeout(10.seconds) {
                        watcher.events.first {
                            it.path == file.toAbsolutePath().normalize() &&
                                (it.type == WorkspaceEventType.CREATED || it.type == WorkspaceEventType.MODIFIED)
                        }
                    }

                assertEquals(file.toAbsolutePath().normalize(), event.path)
            } finally {
                watcher.stop()
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun `emits modify event with hash`() =
        runBlocking {
            val root = Files.createTempDirectory("dispatch-workspace")
            val watcher =
                DefaultWorkspaceWatcher(
                    WorkspaceWatchConfig(
                        root = root,
                        debounce = 0.milliseconds,
                        hashing = HashingMode.ON_MODIFY,
                    ),
                )
            watcher.start()
            try {
                val file = root.resolve("modify.txt")
                file.writeText("first")
                delay(150)
                file.writeText("second")

                val event =
                    withTimeout(5.seconds) {
                        watcher.events.first { it.type == WorkspaceEventType.MODIFIED && it.path == file.toAbsolutePath().normalize() }
                    }

                assertNotNull(event.hash)
            } finally {
                watcher.stop()
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun `registers new subdirectories`() =
        runBlocking {
            val root = Files.createTempDirectory("dispatch-workspace")
            val watcher =
                DefaultWorkspaceWatcher(
                    WorkspaceWatchConfig(
                        root = root,
                        debounce = 0.milliseconds,
                        recursive = true,
                    ),
                )
            watcher.start()
            try {
                val subdir = root.resolve("sub")
                Files.createDirectory(subdir)
                withTimeout(5.seconds) {
                    watcher.events.first {
                        it.path == subdir.toAbsolutePath().normalize() &&
                            (it.type == WorkspaceEventType.CREATED || it.type == WorkspaceEventType.MODIFIED)
                    }
                }
                val file = subdir.resolve("child.txt")
                Files.createFile(file)
                file.writeText("child")

                val event =
                    withTimeout(10.seconds) {
                        watcher.events.first { it.path == file.toAbsolutePath().normalize() }
                    }

                assertEquals(file.toAbsolutePath().normalize(), event.path)
            } finally {
                watcher.stop()
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun `excludes paths by glob`() =
        runBlocking {
            val root = Files.createTempDirectory("dispatch-workspace")
            val watcher =
                DefaultWorkspaceWatcher(
                    WorkspaceWatchConfig(
                        root = root,
                        debounce = 0.milliseconds,
                        excludeGlobs = listOf(".git/**"),
                    ),
                )
            watcher.start()
            try {
                val gitDir = root.resolve(".git")
                Files.createDirectory(gitDir)
                delay(150)
                val file = gitDir.resolve("config")
                file.writeText("ignored")

                val event =
                    withTimeoutOrNull(1.seconds) {
                        watcher.events.first { it.path == file.toAbsolutePath().normalize() }
                    }

                assertNull(event)
            } finally {
                watcher.stop()
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun `debounces successive events`() =
        runBlocking {
            val root = Files.createTempDirectory("dispatch-workspace")
            val debounce = 300.milliseconds
            val watcher =
                DefaultWorkspaceWatcher(
                    WorkspaceWatchConfig(
                        root = root,
                        debounce = debounce,
                    ),
                )
            watcher.start()
            try {
                val firstPath = root.fileSystem.getPath("a.txt")
                val secondPath = root.fileSystem.getPath("b.txt")

                // events is a hot SharedFlow (multi-consumer, no replay), so subscribe BEFORE emitting
                // -- events emitted before a collector is attached are not buffered for it.
                val collected = async { withTimeout(5.seconds) { watcher.events.take(2).toList() } }
                delay(50) // let the collector subscribe before we emit

                watcher.processEventForTest(root, StandardWatchEventKinds.ENTRY_MODIFY, firstPath)
                watcher.processEventForTest(root, StandardWatchEventKinds.ENTRY_MODIFY, secondPath)

                val events = collected.await()
                val delta = events[1].timestamp - events[0].timestamp
                assertTrue(delta >= debounce.inWholeMilliseconds)
            } finally {
                watcher.stop()
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun `emits overflow event`() =
        runBlocking {
            val root = Files.createTempDirectory("dispatch-workspace")
            val watcher =
                DefaultWorkspaceWatcher(
                    WorkspaceWatchConfig(
                        root = root,
                        debounce = 0.milliseconds,
                    ),
                )
            watcher.start()
            try {
                watcher.processEventForTest(root, StandardWatchEventKinds.OVERFLOW, null)
                val event = withTimeout(5.seconds) { watcher.events.first() }

                assertEquals(WorkspaceEventType.OVERFLOW, event.type)
                assertEquals(root.toAbsolutePath().normalize(), event.path)
            } finally {
                watcher.stop()
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun `handles burst events`() =
        runBlocking {
            val root = Files.createTempDirectory("dispatch-workspace")
            val watcher =
                DefaultWorkspaceWatcher(
                    WorkspaceWatchConfig(
                        root = root,
                        debounce = 0.milliseconds,
                    ),
                )
            watcher.start()
            try {
                repeat(100) { index ->
                    val path = root.fileSystem.getPath("file$index.txt")
                    watcher.processEventForTest(root, StandardWatchEventKinds.ENTRY_MODIFY, path)
                }

                val received = mutableListOf<WorkspaceEvent>()
                repeat(100) {
                    received.add(withTimeout(5.seconds) { watcher.events.first() })
                }

                assertEquals(100, received.size)
            } finally {
                watcher.stop()
                root.toFile().deleteRecursively()
            }
        }

    @Test
    fun `follows symlinked directories when enabled`() =
        runBlocking {
            val root = Files.createTempDirectory("dispatch-workspace-root")
            val external = Files.createTempDirectory("dispatch-workspace-external")
            val link = root.resolve("link")
            try {
                runCatching { Files.createSymbolicLink(link, external) }.getOrNull() ?: return@runBlocking

                val watcher =
                    DefaultWorkspaceWatcher(
                        WorkspaceWatchConfig(
                            root = root,
                            debounce = 0.milliseconds,
                            followSymlinks = true,
                        ),
                    )
                watcher.start()
                try {
                    val file = external.resolve("linked.txt")
                    Files.createFile(file)
                    file.writeText("linked")

                    val expectedLinkPath = link.resolve("linked.txt").toAbsolutePath().normalize()
                    val expectedExternalPath = file.toAbsolutePath().normalize()

                    val event =
                        withTimeout(5.seconds) {
                            watcher.events.first {
                                it.path == expectedLinkPath || it.path == expectedExternalPath
                            }
                        }

                    assertTrue(event.path == expectedLinkPath || event.path == expectedExternalPath)
                } finally {
                    watcher.stop()
                }
            } finally {
                root.toFile().deleteRecursively()
                external.toFile().deleteRecursively()
            }
        }
}
