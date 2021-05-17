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

class Queries {
    private var instance: FirebaseFirestore = Firebase.firestore
    private var auth: FirebaseAuth = Firebase.auth

    suspend fun getUser(userId: String): User? {
        return try {
            val ref = instance.collection("users").document(userId).get().await()
            ref.toObject<User>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createUser(user: User): User? {
        return try {
            val ref = auth.signInAnonymously().await()
            ref.user?.uid?.let {
                instance.collection("users").document(it).set(user.toMap()).await()
                user
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserPosts(userId: String): ArrayList<Post?>? {
        return try {
            val ref = instance.collection("posts").document(userId)
                .collection("userPosts").get().await()
            val posts: ArrayList<Post?> = arrayListOf()
            ref.documents.forEach { post ->
                posts.add(post.toObject<Post>())
            }
            posts
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadPost(userId: String, post: Post): Boolean {
        return try {
            instance.collection("posts").document(userId)
                .collection("userPosts").document()
                .set(post.toMap()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun follow(currentUser: String, toBeFollowed: String): Boolean {
        return try {
            instance.collection("following").document(currentUser)
                .collection("userFollowing").document(toBeFollowed)
                .set("").await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun unfollow(currentUser: String, toBeUnfollowed: String): Boolean {
        return try {
            instance.collection("following").document(currentUser)
                .collection("userFollowing").document(toBeUnfollowed)
                .delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getUserFeed(userId: String): ArrayList<Post?>? {
        return try {
            val posts: ArrayList<Post?> = arrayListOf()
            val users = instance.collection("following").document(userId)
                .collection("userFollowing").get().await()
            users.documents.forEach { user ->
               val userPosts = instance.collection("posts").document(user.id)
                   .collection("userPosts").orderBy("created", Query.Direction.DESCENDING)
                   .limit(3).get().await()
               userPosts.documents.forEach { post ->
                   posts.add(post.toObject<Post>())
               }
            }
            posts
        } catch (e: Exception) {
            null
        }
    }
}