package com.upm.gabrau.walkmate.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
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
    /** Holds the value for showing or not the FOLLOW button */
    private val isFollowing: MutableLiveData<Boolean> = MutableLiveData()

    private lateinit var profileUid: String
    private var isCurrentUser: Boolean = false

    /**
     * Initializes the [isFollowing] value for further use in code.
     *
     * Sets up the toolbar and metadata of the user. We also set up the observers for the values
     * [isFollowing].
     * */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        profileUid = intent.getStringExtra("uid")!!
        isFollowing.value = false

        toolbar()
        initFollowerCounts()
        updateList()
        binding.recyclerViewUserPosts.layoutManager = LinearLayoutManager(baseContext)

        CoroutineScope(Dispatchers.Main).launch {
            isFollowing.value = Queries().isUserFollowingUser(profileUid)
            Queries().getCurrentUserId()?.let { uid ->
                isCurrentUser = profileUid == uid
                initFollowButton()
            }
        }

        isFollowing.observe(this, {
            binding.followButton.text = if (it) "UNFOLLOW" else "FOLLOW"
            initFollowerCounts()
        })

        binding.pullToRefreshUsers.setOnRefreshListener {
            updateList()
            binding.pullToRefreshUsers.isRefreshing = false
        }

        binding.fabUserAdd.setOnClickListener{
            val intent = Intent(this, NewPostActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        menu?.findItem(R.id.toolbar_done)?.isVisible = false
        menu?.findItem(R.id.toolbar_search)?.isVisible = false
        return true
    }

    /**
     * Handles the menu buttons. This case the log out button.
     * */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.toolbar_logout -> {
                Queries().logOut()
                val intent = Intent(baseContext, LogInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                true
            }
            else -> true
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

    /**
     * Initializes the followers and following counters by calling the getNumberOfFollowers and
     * getNumberOfFollowing from [Queries]
     * */
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

    /**
     * Initializes the click listener for the FOLLOW button. It calls, depending of the [isFollowing]
     * value the follow ot unfollow functions from [Queries]
     * */
    private fun initFollowButton() {
        if (isCurrentUser) binding.followButton.visibility = View.INVISIBLE
        else {
            binding.followButton.visibility = View.VISIBLE
            binding.followButton.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    isFollowing.value?.let { f ->
                        val q = Queries()
                        if (f) q.unfollow(profileUid)
                        else q.follow(profileUid)
                    }
                    isFollowing.value = !isFollowing.value!!
                }
            }
        }
    }

    /**
     * Updates the adapter of the list of posts. This function is called on the refresh draggable.
     * Called when getUserPosts from [Queries]
     * */
    private fun updateList() {
        val activity = this
        CoroutineScope(Dispatchers.Main).launch {
            val p = Queries().getUserPosts(profileUid)
            if (p != null) {
                posts = p
                val adapter = PostAdapter(baseContext, posts, activity)
                binding.recyclerViewUserPosts.adapter = adapter

                if (isCurrentUser) {
                    val swipeHelper = ItemTouchHelper(
                        SwipeController(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, adapter)
                    )
                    swipeHelper.attachToRecyclerView(binding.recyclerViewUserPosts)
                }
            } else {
                Toast.makeText(baseContext, "Posts could not be retrieved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onItemClicked(post: Post) {
        val intent = Intent(this, NavigationActivity::class.java)
        intent.putExtra("post", post)
        startActivity(intent)
    }
}