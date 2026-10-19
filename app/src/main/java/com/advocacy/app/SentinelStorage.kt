package com.advocacy.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SentinelSyncPayload(
    val version: Int = 1,
    val lastModified: Long = System.currentTimeMillis(),
    val contactName: String = "",
    val contactPhone: String = "",
    val quickAnchors: List<String> = listOf(
        "I need someone to sit with me",
        "I feel overwhelmed",
        "I am in severe pain",
        "I cannot speak right now"
    )
) {
    fun toJson(): String {
        val escapedName = contactName.replace("\"", "\\\"")
        val escapedPhone = contactPhone.replace("\"", "\\\"")
        val anchorsList = quickAnchors.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]"
        ) { "\"" + it.replace("\"", "\\\"") + "\"" }

        return "{\n" +
                "  \"version\": " + version + ",\n" +
                "  \"lastModified\": " + lastModified + ",\n" +
                "  \"contactName\": \"" + escapedName + "\",\n" +
                "  \"contactPhone\": \"" + escapedPhone + "\",\n" +
                "  \"quickAnchors\": " + anchorsList + "\n" +
                "}"
    }

    companion object {
        fun fromJson(json: String): SentinelSyncPayload {
            val name = Regex("\"contactName\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""
            val phone = Regex("\"contactPhone\":\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""
            val anchorsBlock = Regex("\"quickAnchors\":\\s*\\[(.*?)\\]").find(json)?.groupValues?.get(1)
            val anchors = if (!anchorsBlock.isNullOrBlank()) {
                Regex("\"(.*?)\"").findAll(anchorsBlock).map { it.groupValues[1] }.toList()
            } else {
                listOf(
                    "I need someone to sit with me",
                    "I feel overwhelmed",
                    "I am in severe pain",
                    "I cannot speak right now"
                )
            }
            return SentinelSyncPayload(
                contactName = name,
                contactPhone = phone,
                quickAnchors = anchors
            )
        }
    }
}

class SentinelDbHelper(context: Context) : SQLiteOpenHelper(context, "sentinel.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """
        )
        db.execSQL(
            """
            CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT,
                title TEXT,
                body TEXT,
                created INTEGER
            )
            """
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

class SentinelStorageManager(private val context: Context) {
    private val dbHelper = SentinelDbHelper(context)

    fun savePayload(payload: SentinelSyncPayload) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            fun put(key: String, value: String) {
                val cv = ContentValues().apply {
                    put("key", key)
                    put("value", value)
                }
                db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            put("contactName", payload.contactName)
            put("contactPhone", payload.contactPhone)
            put("quickAnchors", payload.quickAnchors.joinToString("||"))
            put("lastModified", payload.lastModified.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun readPayload(): SentinelSyncPayload {
        val db = dbHelper.readableDatabase
        val map = mutableMapOf<String, String>()
        db.rawQuery("SELECT key, value FROM settings", null).use { cursor ->
            while (cursor.moveToNext()) {
                map[cursor.getString(0)] = cursor.getString(1)
            }
        }
        db.close()
        val anchors = map["quickAnchors"]?.split("||")?.filter { it.isNotBlank() } ?: listOf(
            "I need someone to sit with me",
            "I feel overwhelmed",
            "I am in severe pain",
            "I cannot speak right now"
        )
        return SentinelSyncPayload(
            contactName = map["contactName"] ?: "",
            contactPhone = map["contactPhone"] ?: "",
            quickAnchors = anchors,
            lastModified = map["lastModified"]?.toLongOrNull() ?: System.currentTimeMillis()
        )
    }

    fun addNote(type: String, title: String, body: String) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("type", type)
            put("title", title)
            put("body", body)
            put("created", System.currentTimeMillis())
        }
        db.insert("notes", null, cv)
        db.close()
    }

    fun getNotes(): List<Triple<String, String, String>> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<Triple<String, String, String>>()
        db.rawQuery("SELECT type, title, body FROM notes ORDER BY created DESC LIMIT 10", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
            }
        }
        db.close()
        return list
    }
}