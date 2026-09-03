package com.inktodo.app.core.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderSnapshotStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = DeviceProtectedReminderSnapshotStore(context)

    @After
    fun clearStore() {
        store.replaceAll(emptyList())
    }

    @Test
    fun deviceProtectedMirrorRoundTripsAndAtomicallyReplacesSnapshots() {
        val atTime = ScheduledTaskSnapshot(
            taskId = 901,
            title = "上课",
            note = "带书和笔",
            kind = ReminderSnapshotKind.AT_TIME,
            startAt = Instant.parse("2026-08-25T10:00:00Z"),
            useSystemAlarm = true,
        )
        val range = ScheduledTaskSnapshot(
            taskId = 902,
            title = "持续验证",
            kind = ReminderSnapshotKind.TIME_RANGE,
            startAt = Instant.parse("2026-08-25T11:00:00Z"),
            endAt = Instant.parse("2026-08-25T12:00:00Z"),
        )

        store.put(atTime)
        assertEquals(atTime, store.get(901))
        store.replaceAll(listOf(range))

        assertNull(store.get(901))
        assertEquals(listOf(range), store.getAll())
        store.remove(902)
        assertEquals(emptyList<ScheduledTaskSnapshot>(), store.getAll())
    }

    @Test
    fun legacySnapshotWithoutNoteStillDecodesWithNullNote() {
        val legacy = JSONObject()
            .put("taskId", 903)
            .put("title", "旧提醒")
            .put("kind", ReminderSnapshotKind.AT_TIME.name)
            .put("startAt", Instant.parse("2026-08-25T10:00:00Z").toEpochMilli())
            .put("endAt", JSONObject.NULL)
            .put("systemAlarm", false)
            .toString()
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences("reminder_schedule_snapshots", Context.MODE_PRIVATE)
            .edit()
            .putString("task_903", legacy)
            .commit()

        assertNull(store.get(903)?.note)
        assertEquals("旧提醒", store.get(903)?.title)
    }
}
