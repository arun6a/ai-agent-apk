package com.ai.agent.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite database for persistent storage.
 * Stores: conversations, memory (user facts), rules, action log.
 */
class AgentDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "ai_agent.db"
        private const val DB_VERSION = 1

        // Conversations table
        const val TABLE_CONVERSATIONS = "conversations"
        const val COL_ID = "id"
        const val COL_ROLE = "role"  // "user" or "ai"
        const val COL_TEXT = "text"
        const val COL_TIMESTAMP = "timestamp"

        // Memory table (user facts and preferences)
        const val TABLE_MEMORY = "memory"
        const val COL_KEY = "key"
        const val COL_VALUE = "value"

        // Rules table
        const val TABLE_RULES = "rules"
        const val COL_NAME = "name"
        const val COL_TRIGGER_TYPE = "trigger_type"  // "time", "notification"
        const val COL_TRIGGER_VALUE = "trigger_value"  // "07:00", "com.whatsapp:Mom"
        const val COL_ACTION = "action"  // natural language description
        const val COL_ENABLED = "enabled"

        // Action log
        const val TABLE_LOG = "action_log"
        const val COL_ACTION_TEXT = "action_text"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_CONVERSATIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ROLE TEXT,
                $COL_TEXT TEXT,
                $COL_TIMESTAMP INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_MEMORY (
                $COL_KEY TEXT PRIMARY KEY,
                $COL_VALUE TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_RULES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT,
                $COL_TRIGGER_TYPE TEXT,
                $COL_TRIGGER_VALUE TEXT,
                $COL_ACTION TEXT,
                $COL_ENABLED INTEGER DEFAULT 1
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_LOG (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ACTION_TEXT TEXT,
                $COL_TIMESTAMP INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONVERSATIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEMORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RULES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOG")
        onCreate(db)
    }

    // === Conversations ===

    fun addConversation(role: String, text: String) {
        val values = ContentValues().apply {
            put(COL_ROLE, role)
            put(COL_TEXT, text)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_CONVERSATIONS, null, values)
    }

    fun getRecentConversations(limit: Int = 20): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val cursor = readableDatabase.query(
            TABLE_CONVERSATIONS, arrayOf(COL_ROLE, COL_TEXT),
            null, null, null, null,
            "$COL_TIMESTAMP DESC", limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                result.add(it.getString(0) to it.getString(1))
            }
        }
        return result.reversed()  // return in chronological order
    }

    fun clearConversations() {
        writableDatabase.delete(TABLE_CONVERSATIONS, null, null)
    }

    fun clearMemory() {
        writableDatabase.delete(TABLE_MEMORY, null, null)
    }

    // === Memory ===

    fun remember(key: String, value: String) {
        val values = ContentValues().apply {
            put(COL_KEY, key)
            put(COL_VALUE, value)
        }
        writableDatabase.insertWithOnConflict(TABLE_MEMORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun recall(key: String): String? {
        val cursor = readableDatabase.query(
            TABLE_MEMORY, arrayOf(COL_VALUE),
            "$COL_KEY = ?", arrayOf(key),
            null, null, null
        )
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    fun getAllMemory(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val cursor = readableDatabase.query(TABLE_MEMORY, arrayOf(COL_KEY, COL_VALUE), null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                result[it.getString(0)] = it.getString(1)
            }
        }
        return result
    }

    // === Rules ===

    fun addRule(name: String, triggerType: String, triggerValue: String, action: String): Long {
        val values = ContentValues().apply {
            put(COL_NAME, name)
            put(COL_TRIGGER_TYPE, triggerType)
            put(COL_TRIGGER_VALUE, triggerValue)
            put(COL_ACTION, action)
            put(COL_ENABLED, 1)
        }
        return writableDatabase.insert(TABLE_RULES, null, values)
    }

    fun getRules(enabledOnly: Boolean = false): List<Rule> {
        val result = mutableListOf<Rule>()
        val where = if (enabledOnly) "$COL_ENABLED = 1" else null
        val cursor = readableDatabase.query(TABLE_RULES, null, where, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                result.add(Rule(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    name = it.getString(it.getColumnIndexOrThrow(COL_NAME)),
                    triggerType = it.getString(it.getColumnIndexOrThrow(COL_TRIGGER_TYPE)),
                    triggerValue = it.getString(it.getColumnIndexOrThrow(COL_TRIGGER_VALUE)),
                    action = it.getString(it.getColumnIndexOrThrow(COL_ACTION)),
                    enabled = it.getInt(it.getColumnIndexOrThrow(COL_ENABLED)) == 1
                ))
            }
        }
        return result
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete(TABLE_RULES, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        val values = ContentValues().apply { put(COL_ENABLED, if (enabled) 1 else 0) }
        writableDatabase.update(TABLE_RULES, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    // === Action Log ===

    fun logAction(actionText: String) {
        val values = ContentValues().apply {
            put(COL_ACTION_TEXT, actionText)
            put(COL_TIMESTAMP, System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_LOG, null, values)
    }

    data class Rule(
        val id: Long,
        val name: String,
        val triggerType: String,
        val triggerValue: String,
        val action: String,
        val enabled: Boolean
    )
}
