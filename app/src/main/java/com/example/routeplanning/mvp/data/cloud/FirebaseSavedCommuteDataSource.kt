package com.example.routeplanning.mvp.data.cloud

import com.example.routeplanning.mvp.domain.Coordinate
import com.example.routeplanning.mvp.domain.JourneyMode
import com.example.routeplanning.mvp.domain.JourneyProfile
import com.example.routeplanning.mvp.domain.SavedCommute
import com.example.routeplanning.mvp.domain.Weekday
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseSavedCommuteDataSource(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : CloudSavedCommuteDataSource {
    private val authMutex = Mutex()

    override suspend fun authenticate(): String {
        auth.currentUser?.uid?.let { return it }
        return authMutex.withLock {
            auth.currentUser?.uid ?: auth.signInAnonymously()
                .awaitResult()
                .user
                ?.uid
                ?: error("Firebase anonymous authentication returned no user")
        }
    }

    override suspend fun fetchAll(userId: String): List<SavedCommute> =
        collection(userId).get().awaitResult().documents.mapNotNull { document ->
            FirestoreSavedCommuteMapper.fromMap(document.id, document.data.orEmpty())
        }

    override fun observeChanges(userId: String): Flow<CloudSavedCommuteChange> = callbackFlow {
        val registration = collection(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            snapshot?.documentChanges?.forEach { change ->
                when (change.type) {
                    DocumentChange.Type.ADDED,
                    DocumentChange.Type.MODIFIED -> {
                        FirestoreSavedCommuteMapper.fromMap(
                            change.document.id,
                            change.document.data
                        )?.let { trySend(CloudSavedCommuteChange.Upsert(it)) }
                    }

                    DocumentChange.Type.REMOVED ->
                        trySend(CloudSavedCommuteChange.Delete(change.document.id))
                }
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun upsert(userId: String, commute: SavedCommute) {
        collection(userId).document(commute.id)
            .set(FirestoreSavedCommuteMapper.toMap(commute))
            .awaitResult()
    }

    override suspend fun delete(userId: String, id: String) {
        collection(userId).document(id).delete().awaitResult()
    }

    private fun collection(userId: String): CollectionReference = firestore
        .collection(USERS_COLLECTION)
        .document(userId)
        .collection(SAVED_COMMUTES_COLLECTION)

    private companion object {
        const val USERS_COLLECTION = "users"
        const val SAVED_COMMUTES_COLLECTION = "savedCommutes"
    }
}

internal object FirestoreSavedCommuteMapper {
    fun toMap(commute: SavedCommute): Map<String, Any?> = mapOf(
        "schemaVersion" to 1,
        "id" to commute.id,
        "originLabel" to commute.originLabel,
        "destinationLabel" to commute.destinationLabel,
        "originLatitude" to commute.originCoordinate?.latitude,
        "originLongitude" to commute.originCoordinate?.longitude,
        "destinationLatitude" to commute.destinationCoordinate?.latitude,
        "destinationLongitude" to commute.destinationCoordinate?.longitude,
        "departureHour" to commute.departureHour,
        "departureMinute" to commute.departureMinute,
        "activeDays" to commute.activeDays.sortedBy(Weekday::ordinal).map(Weekday::name),
        "mode" to commute.mode.name,
        "profile" to commute.profile.name,
        "createdAtEpochMillis" to commute.createdAtEpochMillis
    )

    fun fromMap(id: String, data: Map<String, Any?>): SavedCommute? = runCatching {
        val originLabel = data["originLabel"] as? String ?: return null
        val destinationLabel = data["destinationLabel"] as? String ?: return null
        val coordinates = listOf(
            data["originLatitude"] as? Number,
            data["originLongitude"] as? Number,
            data["destinationLatitude"] as? Number,
            data["destinationLongitude"] as? Number
        )
        val hasAllCoordinates = coordinates.all { it != null }
        val activeDays = (data["activeDays"] as? List<*>)
            .orEmpty()
            .mapNotNull { value ->
                val name = value as? String
                Weekday.entries.find { it.name == name }
            }
            .toSet()
            .ifEmpty { Weekday.workingDays }

        SavedCommute(
            id = id,
            originLabel = originLabel,
            destinationLabel = destinationLabel,
            originCoordinate = if (hasAllCoordinates) {
                Coordinate(coordinates[0]!!.toDouble(), coordinates[1]!!.toDouble())
            } else {
                null
            },
            destinationCoordinate = if (hasAllCoordinates) {
                Coordinate(coordinates[2]!!.toDouble(), coordinates[3]!!.toDouble())
            } else {
                null
            },
            departureHour = (data["departureHour"] as? Number)?.toInt() ?: return null,
            departureMinute = (data["departureMinute"] as? Number)?.toInt() ?: return null,
            activeDays = activeDays,
            mode = JourneyMode.entries.find { it.name == data["mode"] } ?: JourneyMode.TRANSIT,
            profile = JourneyProfile.entries.find { it.name == data["profile"] }
                ?: JourneyProfile.FASTEST,
            createdAtEpochMillis = (data["createdAtEpochMillis"] as? Number)?.toLong()
                ?: return null
        )
    }.getOrNull()
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase task failed without an exception")
            )
        }
    }
}
