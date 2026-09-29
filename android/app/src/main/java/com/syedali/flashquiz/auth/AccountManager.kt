package com.syedali.flashquiz.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Firebase Auth account manager.
 * Handles sign-up, sign-in, and profile management.
 */
object AccountManager {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun createAccount(email: String, displayName: String, password: String, callback: (Result<String>) -> Unit) {
        if (email.isBlank() || !email.contains("@")) {
            callback(Result.failure(IllegalArgumentException("Invalid email address")))
            return
        }
        if (displayName.isBlank()) {
            callback(Result.failure(IllegalArgumentException("Name is required")))
            return
        }
        if (password.length < 6) {
            callback(Result.failure(IllegalArgumentException("Password must be at least 6 characters")))
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    // Set display name
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()

                    user.updateProfile(profileUpdates)
                        .addOnSuccessListener {
                            callback(Result.success(user.uid))
                        }
                        .addOnFailureListener {
                            // Account created but name update failed — still success
                            callback(Result.success(user.uid))
                        }
                } else {
                    callback(Result.failure(Exception("Account creation failed")))
                }
            }
            .addOnFailureListener { e ->
                val message = when {
                    e.message?.contains("email address is already in use") == true ->
                        "An account with this email already exists"
                    e.message?.contains("badly formatted") == true ->
                        "Invalid email format"
                    e.message?.contains("weak password") == true ->
                        "Password is too weak. Use at least 6 characters"
                    else -> e.message ?: "Account creation failed"
                }
                callback(Result.failure(Exception(message)))
            }
    }

    fun login(email: String, password: String, callback: (Result<String>) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            callback(Result.failure(IllegalArgumentException("Email and password are required")))
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    callback(Result.success(user.uid))
                } else {
                    callback(Result.failure(Exception("Login failed")))
                }
            }
            .addOnFailureListener { e ->
                val message = when {
                    e.message?.contains("no user record") == true ->
                        "No account found with this email"
                    e.message?.contains("password is invalid") == true ->
                        "Incorrect password"
                    e.message?.contains("too many requests") == true ->
                        "Too many attempts. Try again later"
                    else -> e.message ?: "Login failed"
                }
                callback(Result.failure(Exception(message)))
            }
    }

    fun logout() {
        auth.signOut()
    }

    fun deleteAccount(callback: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            callback(Result.failure(Exception("No user logged in")))
            return
        }

        user.delete()
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(e)) }
    }

    fun getDisplayName(): String {
        return auth.currentUser?.displayName ?: ""
    }

    fun getEmail(): String {
        return auth.currentUser?.email ?: ""
    }

    fun getUserId(): String {
        return auth.currentUser?.uid ?: ""
    }

    fun updateProfile(displayName: String? = null, callback: (Result<Unit>) -> Unit = {}) {
        val user = auth.currentUser
        if (user == null) {
            callback(Result.failure(Exception("No user logged in")))
            return
        }

        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .apply {
                displayName?.let { setDisplayName(it) }
            }
            .build()

        user.updateProfile(profileUpdates)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(e)) }
    }
}
