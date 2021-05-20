package com.upm.gabrau.walkmate.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
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
            updateList()
            binding.pullToRefresh.isRefreshing = false
        }

        binding.fab.setOnClickListener{
            val intent = Intent(this, NewPostActivity::class.java)
            startActivity(intent)
        }

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

    private fun updateList() {
        val activity = this
        CoroutineScope(Dispatchers.Main).launch {
            val p = Queries().getUserFeed()
            if (p != null) posts = p
            val adapter = PostAdapter(baseContext, posts, activity)
            binding.recyclerViewPosts.adapter = adapter
        }
    }

    override fun onItemClicked(post: Post) {
        val intent = Intent(this, NavigationActivity::class.java)
        intent.putExtra("post", post)
        startActivity(intent)
    }
}