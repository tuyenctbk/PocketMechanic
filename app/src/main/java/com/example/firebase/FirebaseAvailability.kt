package com.example.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val TAG = "FirebaseAvailability"

/**
 * Safely checks whether Firebase has been configured (e.g., via google-services.json)
 * without throwing [IllegalStateException] on offline, TV, or local-only builds.
 */
object FirebaseAvailability {

    fun isConfigured(context: Context): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                true
            } else {
                FirebaseApp.initializeApp(context) != null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not configured on this build: ${e.message}")
            false
        }
    }

    fun safeAuth(context: Context): FirebaseAuth? {
        if (!isConfigured(context)) return null
        return runCatching { Firebase.auth }.getOrNull()
    }

    fun safeFirestore(context: Context, databaseId: String): FirebaseFirestore? {
        if (!isConfigured(context)) return null
        return runCatching { FirebaseFirestore.getInstance(databaseId) }
            .recoverCatching { FirebaseFirestore.getInstance() }
            .getOrNull()
    }
}
