package com.inktodo.app.core.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderHealthStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-08-27T03:00:00Z")
    private val store = DeviceProtectedReminderHealthStore(
        context,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun healthRecordPersistsInDeviceProtectedStorageAndCanBeCleared() {
        val taskId = 98_041L
        store.remove(taskId)

        store.record(
            taskId,
            ReminderHealthState.FAILED,
            now.plusSeconds(60),
            "Alarm 注册失败",
        )

        val restored = checkNotNull(store.get(taskId))
        assertEquals(ReminderHealthState.FAILED, restored.state)
        assertEquals(now, restored.updatedAt)
        assertEquals(now.plusSeconds(60), restored.nextTriggerAt)
        assertTrue(restored.needsAttention)

        store.remove(taskId)
        assertNull(store.get(taskId))
    }
}
