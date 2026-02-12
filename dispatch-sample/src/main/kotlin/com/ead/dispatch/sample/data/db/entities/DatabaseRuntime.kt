package com.ead.dispatch.sample.data.db.entities

import app.cash.sqldelight.db.SqlDriver
import com.ead.dispatch.sample.DispatchDatabase
import com.ead.dispatch.sample.DispatchDatabaseQueries
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DatabaseRuntime(
    private val factory: DispatchDatabaseFactory = DispatchDatabaseFactory(),
) : AutoCloseable {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dispatch-db")
    }

    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    private val closed = AtomicBoolean(false)
    private val driver: SqlDriver
    val database: DispatchDatabase
    val queries: DispatchDatabaseQueries

    init {
        val initialized = runBlocking {
            withContext(dispatcher) {
                val dbDriver = factory.createDriver()
                dbDriver.execute(null, "PRAGMA foreign_keys=ON", 0)
                val db = DispatchDatabase(dbDriver)
                Triple(dbDriver, db, db.dispatchDatabaseQueries)
            }
        }
        driver = initialized.first
        database = initialized.second
        queries = initialized.third
    }

    suspend fun <T> query(
        block: () -> T,
    ): T = withContext(dispatcher) {
        ensureOpen()
        block()
    }

    suspend fun closeAsync() {
        if (closed.get()) return
        try {
            withContext(dispatcher) {
                if (closed.compareAndSet(false, true)) {
                    driver.close()
                }
            }
        } finally {
            executor.shutdown()
        }
    }

    fun closeBlocking() {
        if (closed.get()) return
        runBlocking {
            closeAsync()
        }
    }

    override fun close() {
        closeBlocking()
    }

    private fun ensureOpen() {
        check(!closed.get()) { "Database runtime is closed." }
    }
}
