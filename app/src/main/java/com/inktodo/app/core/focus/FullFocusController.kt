package com.inktodo.app.core.focus

import android.content.Intent
import com.inktodo.app.core.datastore.FullFocusPreferences
import com.inktodo.app.core.datastore.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class FullFocusStartResult {
    STARTED,
    INVALID_PIN,
    DND_ACCESS_REQUIRED,
    DND_ACTIVATION_FAILED,
}

class FullFocusController(
    private val preferencesRepository: PreferencesRepository,
    private val doNotDisturbController: DoNotDisturbController,
) {
    private val mutex = Mutex()

    val hasDndPolicyAccess: Boolean
        get() = doNotDisturbController.hasPolicyAccess

    fun dndPolicySettingsIntent(): Intent = doNotDisturbController.settingsIntent()

    suspend fun activate(pin: String, enableDnd: Boolean): FullFocusStartResult = mutex.withLock {
        if (!FullFocusPinSecurity.isValid(pin)) return@withLock FullFocusStartResult.INVALID_PIN
        if (enableDnd && !doNotDisturbController.hasPolicyAccess) {
            return@withLock FullFocusStartResult.DND_ACCESS_REQUIRED
        }
        val dndActivation = if (enableDnd) {
            doNotDisturbController.enable()
                ?: return@withLock FullFocusStartResult.DND_ACTIVATION_FAILED
        } else {
            null
        }
        val preferences = preferencesRepository.preferences.first()
        val credential = FullFocusPinSecurity.create(pin)
        preferencesRepository.setFullFocus(
            FullFocusPreferences(
                enabled = true,
                pinSaltBase64 = credential.saltBase64,
                pinVerifierBase64 = credential.verifierBase64,
                dndManaged = dndActivation != null,
                previousInterruptionFilter = dndActivation?.previousInterruptionFilter
                    ?: FullFocusPreferences.DEFAULT_INTERRUPTION_FILTER,
                entryPhase = preferences.pomodoroSession.phase,
                entryTargetEndEpochMillis = preferences.pomodoroSession.targetEndEpochMillis,
            ),
        )
        FullFocusStartResult.STARTED
    }

    suspend fun verifyAndDeactivate(pin: String): Boolean = mutex.withLock {
        val state = preferencesRepository.preferences.first().fullFocus
        if (!state.enabled) return@withLock true
        val verified = FullFocusPinSecurity.verify(
            pin,
            FullFocusCredential(state.pinSaltBase64, state.pinVerifierBase64),
        )
        if (verified) deactivateLocked(state)
        verified
    }

    suspend fun deactivate() = mutex.withLock {
        val state = preferencesRepository.preferences.first().fullFocus
        if (state.enabled || state.dndManaged) deactivateLocked(state)
    }

    private suspend fun deactivateLocked(state: FullFocusPreferences) {
        if (state.dndManaged) {
            doNotDisturbController.disable(DndActivation(state.previousInterruptionFilter))
        }
        preferencesRepository.setFullFocus(FullFocusPreferences())
    }
}
