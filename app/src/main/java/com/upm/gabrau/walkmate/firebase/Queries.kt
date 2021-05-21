package com.upm.gabrau.walkmate.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.upm.gabrau.walkmate.models.Post
import com.upm.gabrau.walkmate.models.User
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class Queries {
    private var instance: FirebaseFirestore = Firebase.firestore
    private var auth: FirebaseAuth = Firebase.auth

    suspend fun getUser(userId: String? = null): User? {
        return try {
            if (auth.currentUser == null) null
            else {
                val ref = instance.collection("users")
                    .document(userId ?: auth.currentUser!!.uid)
                    .get().await()
                ref.toObject<User>()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun logOut() { if (auth.currentUser != null) auth.signOut() }

    fun isUserSessionActive() = auth.currentUser != null

    fun getCurrentUserId(): String? = if (isUserSessionActive()) auth.currentUser!!.uid else null

    suspend fun createUser(email: String, password: String, user: User): User? {
        return try {
            val ref = auth.createUserWithEmailAndPassword(email, password).await()
            ref.user?.uid?.let {
                user.keywords = generateKeywords(user.name!!)
                instance.collection("users").document(it).set(user.toMap()).await()
                user
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signUser(email: String, password: String): Boolean {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getUsersByQuery(query: String): ArrayList<User?> {
        return try {
            if (auth.currentUser == null) arrayListOf()
            else {
                val users: ArrayList<User?> = arrayListOf()
                val ref = instance.collection("users").whereArrayContains("keywords",
                    query.lowercase(Locale.getDefault())).get().await()
                ref.documents.forEach { user ->
                    users.add(user.toObject())
                }
                users
            }
        } catch (e: Exception) {
            arrayListOf()
        }
    }

    suspend fun getUserPosts(userId: String? = null): ArrayList<Post?>? {
        return try {
            if (auth.currentUser == null) null
            else {
                val ref = instance.collection("posts")
                    .whereEqualTo("creator", userId ?: auth.currentUser!!.uid)
                    .get().await()
                val posts: ArrayList<Post?> = arrayListOf()
                ref.documents.forEach { post ->
                    posts.add(post.toObject<Post>())
                }
                posts
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadPost(post: Post): Boolean {
        return try {
            if (auth.currentUser == null) false
            else {
                instance.collection("posts").document().set(post.toMap()).await()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removePost(post: Post): Boolean {
        return try {
            if (auth.currentUser == null) false
            else {
                instance.collection("posts").document(post.id!!).delete().await()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getNumberOfFollowers(userId: String? = null): Int {
        return try {
            if (auth.currentUser == null) 0
            else {
                val ref = instance.collection("follow_system")
                    .whereEqualTo("followee", userId ?: auth.currentUser!!.uid).get().await()
                ref.size()
            }
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getNumberOfFollowing(userId: String? = null): Int {
        return try {
            if (auth.currentUser == null) 0
            else {
                val ref = instance.collection("follow_system")
                    .whereEqualTo("follower", userId ?: auth.currentUser!!.uid).get().await()
                ref.size()
            }
        } catch (e: Exception) {
            0
        }
    }

    suspend fun isUserFollowingUser(otherUser: String): Boolean {
        return try {
            if (auth.currentUser == null) false
            else {
                val id = "${auth.currentUser!!.uid}_$otherUser"
                val ref = instance.collection("follow_system").document(id).get().await()
                ref.exists()
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun follow(toBeFollowed: String): Boolean {
        return try {
            if (auth.currentUser == null) false
            else {
                val id = "${auth.currentUser!!.uid}_$toBeFollowed"
                instance.collection("follow_system").document(id).set(mapOf(
                    "followee" to toBeFollowed,
                    "follower" to auth.currentUser!!.uid
                )).await()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun unfollow(toBeUnfollowed: String): Boolean {
        return try {
            if (auth.currentUser == null) false
            else {
                val id = "${auth.currentUser!!.uid}_$toBeUnfollowed"
                instance.collection("follow_system").document(id).delete().await()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getUserFeed(): ArrayList<Post?>? {
        return try {
            if (auth.currentUser == null) null
            else {
                val posts: ArrayList<Post?> = arrayListOf()
                val users = instance.collection("follow_system")
                    .whereEqualTo("follower", auth.currentUser!!.uid).get().await()
                if (users.isEmpty) null
                else {
                    users.documents.forEach { user ->
                        val id = user.get("followee").toString()
                        val userPosts = instance.collection("posts")
                            .whereEqualTo("creator", id)
                            .orderBy("created", Query.Direction.DESCENDING)
                            .get().await()
                        userPosts.documents.forEach { post ->
                            val p = post.toObject<Post>()
                            if (!posts.contains(p)) posts.add(p)
                        }
                    }
                    posts
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateKeywords(name: String): ArrayList<String> {
        val keywords = arrayListOf<String>()
        name.forEachIndexed { x, _ ->
            keywords.add(name.substring(0, x+1).lowercase(Locale.getDefault()))
        }
        return keywords
    }
}