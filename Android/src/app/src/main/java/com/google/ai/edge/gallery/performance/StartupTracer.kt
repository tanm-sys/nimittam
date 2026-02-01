/*
 * Copyright 2025 Tanmay Patil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.google.ai.edge.gallery.performance

import android.app.Application
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Startup phase tracking.
 */
enum class StartupPhase {
    APPLICATION_CREATE,
    APPLICATION_INIT,
    ACTIVITY_CREATE,
    ACTIVITY_START,
    ACTIVITY_RESUME,
    FIRST_FRAME,
    CONTENT_READY
}

/**
 * Startup timing data for a specific phase.
 * @property phase The startup phase
 * @property elapsedTimeMs Time elapsed since app start
 * @property durationMs Duration of this phase
 */
data class StartupPhaseData(
    val phase: StartupPhase,
    val elapsedTimeMs: Long,
    val durationMs: Long
)

/**
 * Complete startup trace report.
 * @property totalStartupTimeMs Total time from app start to content ready
 * @property phases List of phase timings
 * @property coldStart Whether this was a cold start
 */
data class StartupTrace(
    val totalStartupTimeMs: Long,
    val phases: List<StartupPhaseData>,
    val coldStart: Boolean
)

/**
 * Traces app startup performance.
 * Tracks timing for each startup phase to identify bottlenecks.
 */
@Singleton
class StartupTracer @Inject constructor() {

    private val appStartTime = SystemClock.elapsedRealtime()
    private val phases = mutableListOf<StartupPhaseData>()
    private var lastPhaseTime = appStartTime
    private var isComplete = false

    /**
     * Record a startup phase completion.
     *
     * @param phase The phase that completed
     */
    fun recordPhase(phase: StartupPhase) {
        if (isComplete) return

        val currentTime = SystemClock.elapsedRealtime()
        val elapsedTime = currentTime - appStartTime
        val duration = currentTime - lastPhaseTime

        val phaseData = StartupPhaseData(
            phase = phase,
            elapsedTimeMs = elapsedTime,
            durationMs = duration
        )

        phases.add(phaseData)
        lastPhaseTime = currentTime

        Log.d(TAG, "Phase $phase: ${duration}ms (total: ${elapsedTime}ms)")
    }

    /**
     * Mark startup as complete and generate report.
     *
     * @param coldStart Whether this was a cold start
     * @return Complete startup trace
     */
    fun complete(coldStart: Boolean = true): StartupTrace {
        isComplete = true
        val totalTime = SystemClock.elapsedRealtime() - appStartTime

        return StartupTrace(
            totalStartupTimeMs = totalTime,
            phases = phases.toList(),
            coldStart = coldStart
        ).also {
            Log.i(TAG, "Startup complete: ${totalTime}ms (cold start: $coldStart)")
        }
    }

    /**
     * Get current elapsed time since app start.
     */
    fun getElapsedTimeMs(): Long {
        return SystemClock.elapsedRealtime() - appStartTime
    }

    /**
     * Check if startup is complete.
     */
    fun isStartupComplete(): Boolean = isComplete

    /**
     * Get phases recorded so far.
     */
    fun getPhases(): List<StartupPhaseData> = phases.toList()

    companion object {
        private const val TAG = "StartupTracer"
        private var instance: StartupTracer? = null

        /**
         * Initialize with application for early startup tracking.
         */
        fun init(application: Application): StartupTracer {
            return instance ?: StartupTracer().also {
                instance = it
                it.recordPhase(StartupPhase.APPLICATION_CREATE)
            }
        }

        /**
         * Get the singleton instance.
         */
        fun getInstance(): StartupTracer {
            return instance ?: throw IllegalStateException(
                "StartupTracer not initialized. Call init() in Application.onCreate()"
            )
        }
    }
}

/**
 * Extension function for easy phase recording.
 */
fun StartupTracer.tracePhase(phase: StartupPhase, block: () -> Unit) {
    val startTime = SystemClock.elapsedRealtime()
    block()
    recordPhase(phase)
}

/**
 * Suspended version for coroutines.
 */
suspend fun <T> StartupTracer.tracePhaseAsync(phase: StartupPhase, block: suspend () -> T): T {
    val result = block()
    recordPhase(phase)
    return result
}
