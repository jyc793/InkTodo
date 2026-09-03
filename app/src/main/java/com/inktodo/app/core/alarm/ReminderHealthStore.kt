package com.inktodo.app.core.alarm

import android.content.Context
import java.time.Clock
import java.time.Instant
import org.json.JSONObject

enum class ReminderHealthState {
    SCHEDULED,
    SCHEDULED_WITH_WARNING,
    TRIGGERED,
    POSTED_TO_SYSTEM,
    AWAITING_CONFIRMATION,
    ACKNOWLEDGED,
    FAILED,
    DROPPED,
    CANCELLED,
}

data class ReminderHealthRecord(
    val taskId: Long,
    val state: ReminderHealthState,
    val updatedAt: Instant,
    val nextTriggerAt: Instant? = null,
    val detail: String? = null,
) {
    val needsAttention: Boolean
        get() = state == ReminderHealthState.FAILED || state == ReminderHealthState.DROPPED
}

interface ReminderHealthStore {
    fun get(taskId: Long): ReminderHealthRecord?
    fun getAll(): List<ReminderHealthRecord>
    fun record(
        taskId: Long,
        state: ReminderHealthState,
        nextTriggerAt: Instant? = null,
        detail: String? = null,
    )
    fun remove(taskId: Long)
}

object NoOpReminderHealthStore : ReminderHealthStore {
    override fun get(taskId: Long): ReminderHealthRecord? = null
    override fun getAll(): List<ReminderHealthRecord> = emptyList()
    override fun record(
        taskId: Long,
        state: ReminderHealthState,
        nextTriggerAt: Instant?,
        detail: String?,
    ) = Unit
    override fun remove(taskId: Long) = Unit
}

class DeviceProtectedReminderHealthStore(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : ReminderHealthStore {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    override fun get(taskId: Long): ReminderHealthRecord? =
        preferences.getString(key(taskId), null)?.let(::decode)

    override fun getAll(): List<ReminderHealthRecord> = preferences.all.values
        .mapNotNull { (it as? String)?.let(::decode) }
        .sortedByDescending(ReminderHealthRecord::updatedAt)

    override fun record(
        taskId: Long,
        state: ReminderHealthState,
        nextTriggerAt: Instant?,
        detail: String?,
    ) {
        val value = ReminderHealthRecord(
            taskId = taskId,
            state = state,
            updatedAt = Instant.now(clock),
            nextTriggerAt = nextTriggerAt,
            detail = detail?.take(MAX_DETAIL_LENGTH),
        )
        check(preferences.edit().putString(key(taskId), encode(value)).commit()) {
            "Failed to persist reminder health for $taskId"
        }
    }

    override fun remove(taskId: Long) {
        check(preferences.edit().remove(key(taskId)).commit()) {
            "Failed to remove reminder health for $taskId"
        }
    }

    private fun encode(record: ReminderHealthRecord): String = JSONObject()
        .put(FIELD_TASK_ID, record.taskId)
        .put(FIELD_STATE, record.state.name)
        .put(FIELD_UPDATED_AT, record.updatedAt.toEpochMilli())
        .put(FIELD_NEXT_TRIGGER_AT, record.nextTriggerAt?.toEpochMilli() ?: JSONObject.NULL)
        .put(FIELD_DETAIL, record.detail ?: JSONObject.NULL)
        .toString()

    private fun decode(raw: String): ReminderHealthRecord? = runCatching {
        val json = JSONObject(raw)
        ReminderHealthRecord(
            taskId = json.getLong(FIELD_TASK_ID),
            state = ReminderHealthState.valueOf(json.getString(FIELD_STATE)),
            updatedAt = Instant.ofEpochMilli(json.getLong(FIELD_UPDATED_AT)),
            nextTriggerAt = json.optLong(FIELD_NEXT_TRIGGER_AT)
                .takeIf { !json.isNull(FIELD_NEXT_TRIGGER_AT) }
                ?.let(Instant::ofEpochMilli),
            detail = json.optString(FIELD_DETAIL).takeIf { !json.isNull(FIELD_DETAIL) },
        )
    }.getOrNull()

    private fun key(taskId: Long): String = "$KEY_PREFIX$taskId"

    private companion object {
        const val STORE_NAME = "reminder_health"
        const val KEY_PREFIX = "task_"
        const val MAX_DETAIL_LENGTH = 240
        const val FIELD_TASK_ID = "taskId"
        const val FIELD_STATE = "state"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_NEXT_TRIGGER_AT = "nextTriggerAt"
        const val FIELD_DETAIL = "detail"
    }
}
