package com.ead.dispatch.sample.data.db.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseRuntimeTest {

    @Test
    fun `database runtime confines all queries to one thread`() = runBlocking {
        val tempDir = createTempDirectory("dispatch-db-runtime-test")
        val runtime = DatabaseRuntime(
            factory = DispatchDatabaseFactory(
                appName = "dispatch-test",
                appAuthor = "ead-test",
                dbFileName = "runtime-${UUID.randomUUID()}.db",
                dataDirectoryOverride = tempDir,
            )
        )

        try {
            val threadId = runtime.query { Thread.currentThread().threadId() }

            repeat(10) {
                val observed = withContext(Dispatchers.Default) {
                    runtime.query { Thread.currentThread().threadId() }
                }
                assertEquals(
                    expected = threadId,
                    actual = observed,
                    message = "DB query executed outside dedicated runtime thread.",
                )
            }
        } finally {
            runtime.closeBlocking()
        }
    }
}
