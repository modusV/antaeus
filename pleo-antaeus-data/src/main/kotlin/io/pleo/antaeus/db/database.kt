package io.pleo.antaeus.db

import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.data.CustomerTable
import io.pleo.antaeus.data.InvoiceTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.Connection


fun createDb (dbname : String = "antaeus-db", log : Boolean = true) : AntaeusDal {

    // The tables to create in the database.
    val tables = arrayOf(InvoiceTable, CustomerTable)

    val dbFile: File = File.createTempFile(dbname, ".sqlite")
    // Connect to the database and create the needed tables. Drop any existing data.
    val db = Database
        .connect(url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC",
            user = "root",
            password = "")
        .also {
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            transaction(it) {
                if (log) {
                    addLogger(StdOutSqlLogger)
                }
                // Drop all existing tables to ensure a clean slate on each run
                SchemaUtils.drop(*tables)
                // Create all tables
                SchemaUtils.create(*tables)
            }
        }

    return AntaeusDal(db)
}