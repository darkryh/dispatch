package com.ead.dispatch.sample.data.db.entities

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ead.dispatch.sample.DispatchDatabase
import net.harawata.appdirs.AppDirsFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

class DispatchDatabaseFactory(
    private val appName: String = "dispatch",
    private val appAuthor: String = "ead",
    private val dbFileName: String = "dispatch.db",
) {

    fun create(): DispatchDatabase {
        val driver = createDriver()
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        return DispatchDatabase(driver)
    }

    private fun createDriver(): SqlDriver {
        val dataDir = Paths.get(
            AppDirsFactory.getInstance().getUserDataDir(appName, null, appAuthor)
        )

        Files.createDirectories(dataDir.toAbsolutePath())
        val dbPath = dataDir.resolve(dbFileName).toAbsolutePath().toString()
        val useInMemory = System.getenv("DISPATCH_DB_IN_MEMORY")?.equals("true", ignoreCase = true) == true
        val jdbcUrl = if (useInMemory) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$dbPath"
        return JdbcSqliteDriver(jdbcUrl, Properties(), DispatchDatabase.Schema)
    }
}
