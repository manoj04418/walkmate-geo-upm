package com.upm.gabrau.walkmate.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.databinding.ActivityProfileBinding
import com.upm.gabrau.walkmate.firebase.Queries
import com.upm.gabrau.walkmate.models.Post
import com.upm.gabrau.walkmate.utils.PostAdapter
import com.upm.gabrau.walkmate.utils.SwipeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity(), PostAdapter.OnItemClickListener {
    private lateinit var binding: ActivityProfileBinding
    private var posts = arrayListOf<Post?>()

    private lateinit var profileUid: String
    private var isFollowing: Boolean = false
    private var isCurrentUser: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        profileUid = intent.getStringExtra("uid")!!

        toolbar()
        initFollowerCounts()
        updateList()
        binding.recyclerViewUserPosts.layoutManager = LinearLayoutManager(baseContext)

        CoroutineScope(Dispatchers.Main).launch {
            isFollowing = Queries().isUserFollowingUser(profileUid)
            Queries().getCurrentUserId()?.let { uid ->
                isCurrentUser = profileUid == uid
                initFollowButton()
            }
        }

        binding.pullToRefreshUsers.setOnRefreshListener {
            updateList()
            binding.pullToRefreshUsers.isRefreshing = false
        }

        binding.fabUserAdd.setOnClickListener{
            val intent = Intent(this, NewPostActivity::class.java)
            startActivity(intent)
        }
    }

    private fun toolbar() {
        setSupportActionBar(binding.toolbar.root)
        findViewById<ImageView>(R.id.backpack).visibility = View.GONE
        CoroutineScope(Dispatchers.Main).launch {
            val user = Queries().getUser(profileUid)
            user?.let { supportActionBar?.title = it.name }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.root.setNavigationOnClickListener { onBackPressed() }
    }

    private fun initFollowerCounts() {
        CoroutineScope(Dispatchers.Main).launch {
            val q = Queries()
            val followers = q.getNumberOfFollowers(profileUid)
            val following = q.getNumberOfFollowing(profileUid)
            val followerText = "Followers: $followers"
            val followingText = "Following: $following"
            binding.textViewFollowers.text = followerText
            binding.textViewFollowing.text = followingText
        }
    }

    private fun initFollowButton() {
        if (isCurrentUser) binding.followButton.visibility = View.INVISIBLE
        else {
            binding.followButton.visibility = View.VISIBLE
            binding.followButton.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val q = Queries()
                    if (isFollowing) q.unfollow(profileUid)
                    else q.follow(profileUid)
                }
            }
        }
    }

    private fun updateList() {
        val activity = this
        CoroutineScope(Dispatchers.Main).launch {
            val p = Queries().getUserPosts(profileUid)
            if (p != null) posts = p
            val adapter = PostAdapter(baseContext, posts, activity)
            binding.recyclerViewUserPosts.adapter = adapter

            if (isCurrentUser) {
                val swipeHelper = ItemTouchHelper(
                    SwipeController(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, adapter)
                )
                swipeHelper.attachToRecyclerView(binding.recyclerViewUserPosts)
            }
        }
    }

    override fun onItemClicked(post: Post) {
        val intent = Intent(this, NavigationActivity::class.java)
        intent.putExtra("post", post)
        startActivity(intent)
    }
}