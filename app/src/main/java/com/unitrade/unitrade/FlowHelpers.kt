package com.unitrade.unitrade

import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ListenerRegistration

/**
 * FlowHelpers.firestoreSnapshotFlow
 * - Lokasi: app/src/main/java/com/unitrade/unitrade/util/FlowHelpers.kt
 * - Membuat callbackFlow dari Query.addSnapshotListener,
 *   dan memetakan QuerySnapshot ke tipe T melalui mapper.
 */
object FlowHelpers {
    fun <T> firestoreSnapshotFlow(query: Query, mapper: (QuerySnapshot?) -> T): Flow<T> = callbackFlow {
        val registration: ListenerRegistration = query.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            trySend(mapper(snap))
        }
        awaitClose { registration.remove() }
    }
}
