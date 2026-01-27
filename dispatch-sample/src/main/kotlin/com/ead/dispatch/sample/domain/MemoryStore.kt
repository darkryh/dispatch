package com.ead.dispatch.sample.domain

import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.providers.LocalFileMemoryProvider
import ai.koog.agents.memory.providers.LocalMemoryConfig
import ai.koog.agents.memory.storage.SimpleStorage
import ai.koog.rag.base.files.JVMFileSystemProvider
import java.nio.file.Path
import kotlin.io.path.pathString

object MemoryStore {
    private val appDirectory: Path = Pathing.applicationDirectory
    private val memoryPath = Path.of("memory")

    private val fs = JVMFileSystemProvider.ReadWrite
    private val storage = SimpleStorage(fs)

    val provider = LocalFileMemoryProvider(
        LocalMemoryConfig(memoryPath.pathString, MemoryScope.Product("dispatch")),
        storage,
        fs,
        appDirectory,
        )
}
