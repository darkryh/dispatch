package com.ead.dispatch.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.FileVisitResult.SKIP_SUBTREE
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.security.MessageDigest
import java.util.EnumSet
import java.util.HexFormat

interface WorkspaceWatcher {
    val events: SharedFlow<WorkspaceEvent>
    fun start()
    fun stop()
}

class DefaultWorkspaceWatcher(
    private val config: WorkspaceWatchConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) : WorkspaceWatcher, AutoCloseable {
    private var scope: CoroutineScope = newScope()
    private val root = config.root.toAbsolutePath().normalize()
    private val includeMatchers = buildMatchers(config.includeGlobs)
    private val excludeMatchers = buildMatchers(config.excludeGlobs)

    // A SharedFlow lets multiple composables observe the same event stream without
    // stealing events from one another (a single-consumer Channel would).
    private val eventFlow = MutableSharedFlow<WorkspaceEvent>(
        replay = config.maxEventBatchSize,
        extraBufferCapacity = config.maxEventBatchSize,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var job: Job? = null
    private var watchService: WatchService? = null
    private val keys = mutableMapOf<WatchKey, Path>()
    private var lastEmissionAt = 0L

    override val events: SharedFlow<WorkspaceEvent> = eventFlow.asSharedFlow()

    override fun start() {
        if (job?.isActive == true) return
        require(Files.exists(root)) { "Workspace root does not exist: $root" }
        require(Files.isDirectory(root)) { "Workspace root is not a directory: $root" }

        if (!scope.isActive) {
            scope = newScope()
        }

        watchService = FileSystems.getDefault().newWatchService()
        keys.clear()
        lastEmissionAt = 0L
        registerRoot()
        job = scope.launch { pollLoop() }
    }

    override fun stop() {
        job?.cancel()
        job = null
        keys.clear()
        watchService?.close()
        watchService = null
        scope.cancel()
    }

    override fun close() {
        stop()
    }

    internal fun isScopeActiveForTest(): Boolean = scope.isActive

    internal fun isWatchServiceOpenForTest(): Boolean = watchService != null

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun registerRoot() {
        if (config.recursive) {
            val options = if (config.followSymlinks) {
                EnumSet.of(FileVisitOption.FOLLOW_LINKS)
            } else {
                EnumSet.noneOf(FileVisitOption::class.java)
            }
            Files.walkFileTree(root, options, Int.MAX_VALUE, object : FileVisitor<Path> {
                override fun preVisitDirectory(dir: Path, attrs: java.nio.file.attribute.BasicFileAttributes): FileVisitResult {
                    registerDirectory(dir)
                    return CONTINUE
                }

                override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): FileVisitResult = CONTINUE

                override fun visitFileFailed(file: Path, exc: java.io.IOException?): FileVisitResult = SKIP_SUBTREE

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult = CONTINUE
            })
        } else {
            registerDirectory(root)
        }
    }

    private fun registerDirectory(dir: Path) {
        val service = watchService ?: return
        val key = dir.register(
            service,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE,
        )
        keys[key] = dir
    }

    private suspend fun pollLoop() {
        val service = watchService ?: return
        while (currentCoroutineContext().isActive) {
            val key = try {
                service.take()
            } catch (_: java.nio.file.ClosedWatchServiceException) {
                return
            }

            val dir = keys[key] ?: continue
            val events = key.pollEvents()
            for (event in events) {
                processEvent(dir, event.kind(), event.context() as? Path)
            }

            if (!key.reset()) {
                keys.remove(key)
            }
        }
    }

    internal suspend fun processEventForTest(dir: Path, kind: WatchEvent.Kind<*>, context: Path? = null) {
        processEvent(dir, kind, context)
    }

    private suspend fun processEvent(dir: Path, kind: WatchEvent.Kind<*>, context: Path?) {
        if (kind == StandardWatchEventKinds.OVERFLOW) {
            emitEvent(root, WorkspaceEventType.OVERFLOW, null)
            return
        }

        val resolvedContext = context ?: return
        val resolved = dir.resolve(resolvedContext).toAbsolutePath().normalize()

        if (config.recursive && kind == StandardWatchEventKinds.ENTRY_CREATE) {
            registerIfDirectory(resolved)
        }

        if (!shouldEmit(resolved)) return

        val type = when (kind) {
            StandardWatchEventKinds.ENTRY_CREATE -> WorkspaceEventType.CREATED
            StandardWatchEventKinds.ENTRY_MODIFY -> WorkspaceEventType.MODIFIED
            StandardWatchEventKinds.ENTRY_DELETE -> WorkspaceEventType.DELETED
            else -> return
        }

        val hash = when (config.hashing) {
            HashingMode.NONE -> null
            HashingMode.ON_MODIFY -> if (type == WorkspaceEventType.MODIFIED) hashFile(resolved) else null
            HashingMode.ALWAYS -> if (type == WorkspaceEventType.DELETED) null else hashFile(resolved)
        }

        emitEvent(resolved, type, hash)
    }

    private fun registerIfDirectory(path: Path) {
        val isDir = if (config.followSymlinks) {
            Files.isDirectory(path)
        } else {
            Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
        }
        if (isDir) {
            registerDirectory(path)
        }
    }

    private suspend fun emitEvent(path: Path, type: WorkspaceEventType, hash: String?) {
        val debounceMs = config.debounce.inWholeMilliseconds
        if (debounceMs > 0) {
            val now = clock()
            val elapsed = now - lastEmissionAt
            if (elapsed in 0 until debounceMs) {
                delay(debounceMs - elapsed)
            }
        }
        lastEmissionAt = clock()
        eventFlow.emit(WorkspaceEvent(path, type, lastEmissionAt, hash))
    }

    private fun shouldEmit(path: Path): Boolean {
        if (!path.startsWith(root)) return false
        val relative = root.relativize(path)
        if (excludeMatchers.any { it.matches(relative) }) return false
        if (includeMatchers.isEmpty()) return true
        return includeMatchers.any { it.matches(relative) }
    }

    internal fun hashFile(path: Path): String? {
        if (!Files.exists(path)) return null
        if (Files.isDirectory(path)) return null
        return try {
            // MessageDigest is not thread-safe; create a fresh instance per call so concurrent
            // hashing from the watcher coroutine cannot corrupt shared digest state.
            val digest = MessageDigest.getInstance(config.hashAlgorithm)
            Files.newInputStream(path).use { input ->
                updateDigest(digest, input)
            }
            // HexFormat does the same lowercase, zero-padded hex but without spinning up a
            // Formatter + intermediate String per byte.
            HexFormat.of().formatHex(digest.digest())
        } catch (exception: Exception) {
            WorkspaceLog.debug("hashFile failed for $path", exception)
            null
        }
    }

    private fun updateDigest(digest: MessageDigest, input: InputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read = input.read(buffer)
        while (read >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read)
            }
            read = input.read(buffer)
        }
    }

    private fun buildMatchers(globs: List<String>): List<PathMatcher> {
        if (globs.isEmpty()) return emptyList()
        val fileSystem = root.fileSystem
        return globs.map { glob ->
            val normalized = normalizeGlob(glob)
            fileSystem.getPathMatcher("glob:$normalized")
        }
    }

    private fun normalizeGlob(glob: String): String {
        val separator = fileSystemSeparator()
        return if (separator == '/') {
            glob
        } else {
            glob.replace('/', separator)
        }
    }

    private fun fileSystemSeparator(): Char {
        return root.fileSystem.separator.firstOrNull() ?: '/'
    }
}
