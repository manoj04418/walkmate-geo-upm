package com.upm.gabrau.walkmate.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.databinding.ActivityMainBinding
import com.upm.gabrau.walkmate.firebase.Queries
import com.upm.gabrau.walkmate.models.Post
import com.upm.gabrau.walkmate.utils.PostAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), PostAdapter.OnItemClickListener {
    private lateinit var binding: ActivityMainBinding
    private var posts = arrayListOf<Post?>()

    /**
     * Creates and initializes the basic views
     * */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setSupportActionBar(binding.toolbar.root)
        supportActionBar?.title = "Feed"

        updateList()

        binding.recyclerViewPosts.layoutManager = LinearLayoutManager(baseContext)

        binding.pullToRefresh.setOnRefreshListener {
            binding.pullToRefresh.isRefreshing = true
            updateList()
            binding.pullToRefresh.isRefreshing = false
        }

        binding.fab.setOnClickListener {
            val intent = Intent(this, NewPostActivity::class.java)
            startActivity(intent)
        }

        binding.refreshOnEmpty.setOnClickListener { updateList() }

        findViewById<ImageView>(R.id.backpack).setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                Queries().getCurrentUserId()?.let { uid ->
                    val intent = Intent(baseContext, ProfileActivity::class.java)
                    intent.putExtra("uid", uid)
                    startActivity(intent)
                }
            }
        }
    }

    /**
     * Inflates the menu and hides unnecessary buttons
     * */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        menu?.findItem(R.id.toolbar_done)?.isVisible = false
        menu?.findItem(R.id.toolbar_logout)?.isVisible = false
        return true
    }

    /**
     * Manages the click on the visible buttons, this case the Search
     * */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.toolbar_search -> {
                val intent = Intent(baseContext, SearchActivity::class.java)
                startActivity(intent)
                true
            }
            else -> true
        }
    }

    /**
     * This function updates the adapter of the RecyclerView everytime it is called.
     * It is invoked inside the refresh draggable, so everytime the user refreshes the list
     * by dragging the list from the top, the getUserFeed function from [Queries] is called
     * retrieving the latest posts of the following people of the user, and updating the adaptor
     * as well as the local variable [posts]
     * */
    private fun updateList() {
        val activity = this
        CoroutineScope(Dispatchers.Main).launch {
            val p = Queries().getUserFeed()
            lateinit var adapter: PostAdapter
            if (p != null) {
                binding.refreshOnEmpty.visibility = View.INVISIBLE
                posts = p
                adapter = PostAdapter(baseContext, posts, activity)
            } else {
                binding.refreshOnEmpty.visibility = View.VISIBLE
                adapter = PostAdapter(baseContext, arrayListOf(), activity)
                Toast.makeText(baseContext, "Posts could not be retrieved", Toast.LENGTH_SHORT).show()
            }
            binding.recyclerViewPosts.adapter = adapter
        }
    }

    override fun onItemClicked(post: Post) {
        val intent = Intent(this, NavigationActivity::class.java)
        intent.putExtra("post", post)
        startActivity(intent)
    }
}