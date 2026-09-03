package com.inktodo.app.core.alarm

import android.content.Context
import org.json.JSONObject

interface ReminderSnapshotStore {
    fun get(taskId: Long): ScheduledTaskSnapshot?
    fun getAll(): List<ScheduledTaskSnapshot>
    fun put(snapshot: ScheduledTaskSnapshot)
    fun remove(taskId: Long)
    fun replaceAll(snapshots: Collection<ScheduledTaskSnapshot>)
}

class DeviceProtectedReminderSnapshotStore(context: Context) : ReminderSnapshotStore {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    override fun get(taskId: Long): ScheduledTaskSnapshot? =
        preferences.getString(key(taskId), null)?.let(::decode)

    override fun getAll(): List<ScheduledTaskSnapshot> = preferences.all.values
        .mapNotNull { value -> (value as? String)?.let(::decode) }
        .sortedBy(ScheduledTaskSnapshot::startAt)

    override fun put(snapshot: ScheduledTaskSnapshot) {
        check(preferences.edit().putString(key(snapshot.taskId), encode(snapshot)).commit()) {
            "Failed to persist reminder snapshot ${snapshot.taskId}"
        }
    }

    override fun remove(taskId: Long) {
        check(preferences.edit().remove(key(taskId)).commit()) {
            "Failed to remove reminder snapshot $taskId"
        }
    }

    override fun replaceAll(snapshots: Collection<ScheduledTaskSnapshot>) {
        val editor = preferences.edit().clear()
        snapshots.forEach { editor.putString(key(it.taskId), encode(it)) }
        check(editor.commit()) { "Failed to replace reminder snapshots" }
    }

    private fun encode(snapshot: ScheduledTaskSnapshot): String = JSONObject()
        .put(FIELD_TASK_ID, snapshot.taskId)
        .put(FIELD_TITLE, snapshot.title)
        .put(FIELD_NOTE, snapshot.note ?: JSONObject.NULL)
        .put(FIELD_KIND, snapshot.kind.name)
        .put(FIELD_START_AT, snapshot.startAt.toEpochMilli())
        .put(FIELD_END_AT, snapshot.endAt?.toEpochMilli() ?: JSONObject.NULL)
        .put(FIELD_SYSTEM_ALARM, snapshot.useSystemAlarm)
        .toString()

    private fun decode(raw: String): ScheduledTaskSnapshot? = runCatching {
        val json = JSONObject(raw)
        ScheduledTaskSnapshot(
            taskId = json.getLong(FIELD_TASK_ID),
            title = json.getString(FIELD_TITLE),
            note = json.optString(FIELD_NOTE, "").trim().takeIf(String::isNotEmpty),
            kind = ReminderSnapshotKind.valueOf(json.getString(FIELD_KIND)),
            startAt = java.time.Instant.ofEpochMilli(json.getLong(FIELD_START_AT)),
            endAt = json.optLong(FIELD_END_AT).takeIf { !json.isNull(FIELD_END_AT) }
                ?.let(java.time.Instant::ofEpochMilli),
            useSystemAlarm = json.optBoolean(FIELD_SYSTEM_ALARM, false),
        )
    }.getOrNull()

    private fun key(taskId: Long): String = "$KEY_PREFIX$taskId"

    private companion object {
        const val STORE_NAME = "reminder_schedule_snapshots"
        const val KEY_PREFIX = "task_"
        const val FIELD_TASK_ID = "taskId"
        const val FIELD_TITLE = "title"
        const val FIELD_NOTE = "note"
        const val FIELD_KIND = "kind"
        const val FIELD_START_AT = "startAt"
        const val FIELD_END_AT = "endAt"
        const val FIELD_SYSTEM_ALARM = "systemAlarm"
    }
}
