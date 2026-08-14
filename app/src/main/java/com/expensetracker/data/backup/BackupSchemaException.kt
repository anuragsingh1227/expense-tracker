package com.expensetracker.data.backup

/**
 * Thrown when a JSON backup fails schema validation. Callers must not wipe
 * or mutate the existing local database after this is thrown.
 */
class BackupSchemaException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
