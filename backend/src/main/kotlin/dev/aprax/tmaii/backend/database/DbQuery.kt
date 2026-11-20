package dev.aprax.tmaii.backend.database

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

/** Runs [block] under the caller's transaction when one is already open, otherwise opens its own. */
fun <T> dbQuery(block: () -> T): T =
    if (TransactionManager.currentOrNull() != null) block() else transaction { block() }
