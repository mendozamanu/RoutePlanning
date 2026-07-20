package com.example.routeplanning.mvp.data.cloud

import android.util.Log
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.SavedCommuteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncingSavedCommuteRepository(
    private val local: SavedCommuteRepository,
    private val cloud: CloudSavedCommuteDataSource,
    private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS
) : SavedCommuteRepository {
    @Volatile
    private var cachedUserId: String? = null

    init {
        syncScope.launch { synchronizeContinuously() }
    }

    override fun observeAll(): Flow<List<SavedCommute>> = local.observeAll()

    override suspend fun findById(id: String): SavedCommute? = local.findById(id)

    override suspend fun upsert(commute: SavedCommute) {
        local.upsert(commute)
        syncScope.launch {
            runCloudOperation("upload") { userId -> cloud.upsert(userId, commute) }
        }
    }

    override suspend fun delete(id: String) {
        local.delete(id)
        syncScope.launch {
            runCloudOperation("delete") { userId -> cloud.delete(userId, id) }
        }
    }

    private suspend fun synchronizeContinuously() {
        while (currentCoroutineContext().isActive) {
            try {
                val userId = userId()
                mergeInitialState(userId)
                cloud.observeChanges(userId).collect { change ->
                    when (change) {
                        is CloudSavedCommuteChange.Upsert -> mergeRemote(change.commute)
                        is CloudSavedCommuteChange.Delete -> local.delete(change.id)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Cloud sync unavailable; local storage remains active", error)
                delay(retryDelayMillis)
            }
        }
    }

    private suspend fun mergeInitialState(userId: String) {
        val localCommutes = local.observeAll().first().associateBy(SavedCommute::id)
        val cloudCommutes = cloud.fetchAll(userId).associateBy(SavedCommute::id)

        cloudCommutes.values.forEach { remote ->
            val localVersion = localCommutes[remote.id]
            if (localVersion == null || remote.createdAtEpochMillis > localVersion.createdAtEpochMillis) {
                local.upsert(remote)
            }
        }
        localCommutes.values.forEach { localVersion ->
            val remote = cloudCommutes[localVersion.id]
            if (remote == null || localVersion.createdAtEpochMillis >= remote.createdAtEpochMillis) {
                cloud.upsert(userId, localVersion)
            }
        }
    }

    private suspend fun mergeRemote(remote: SavedCommute) {
        val localVersion = local.findById(remote.id)
        if (localVersion == null || remote.createdAtEpochMillis >= localVersion.createdAtEpochMillis) {
            local.upsert(remote)
        }
    }

    private suspend fun runCloudOperation(
        operation: String,
        action: suspend (String) -> Unit
    ) {
        try {
            action(userId())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not $operation saved journey; Firestore sync will retry", error)
        }
    }

    private suspend fun userId(): String = cachedUserId ?: cloud.authenticate().also {
        cachedUserId = it
    }

    private companion object {
        const val TAG = "SavedCommuteSync"
        const val DEFAULT_RETRY_DELAY_MILLIS = 30_000L
    }
}
