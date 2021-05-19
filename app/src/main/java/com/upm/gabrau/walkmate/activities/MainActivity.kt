package com.upm.gabrau.walkmate.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.upm.gabrau.walkmate.databinding.ActivityMainBinding
import com.upm.gabrau.walkmate.firebase.Queries
import com.upm.gabrau.walkmate.models.Post
import com.upm.gabrau.walkmate.utils.PostAdapter
import com.upm.gabrau.walkmate.utils.SwipeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), PostAdapter.OnItemClickListener {
    companion object {
        const val LAUNCH_DETAIL_POST_ACTIVITY = 1
        const val LAUNCH_NEW_POST_ACTIVITY = 2
    }

    private lateinit var binding: ActivityMainBinding
    private var posts = arrayListOf<Post?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setSupportActionBar(binding.toolbar.root)

        updateList()

        binding.recyclerViewPosts.layoutManager = LinearLayoutManager(baseContext)

        binding.pullToRefresh.setOnRefreshListener {
            updateList()
            binding.pullToRefresh.isRefreshing = false
        }

        binding.fab.setOnClickListener{ goToDetailActivity() }
    }

    private fun goToDetailActivity() {
        val intent = Intent(this, NewPostActivity::class.java)
        startActivityForResult(intent, LAUNCH_NEW_POST_ACTIVITY)
    }

    private fun updateList() {
        val activity = this
        CoroutineScope(Dispatchers.Main).launch {
            val p = Queries().getUserPosts()
            if (p != null) posts = p
            val adapter = PostAdapter(baseContext, posts, activity)
            binding.recyclerViewPosts.adapter = adapter
            val swipeHelper = ItemTouchHelper(
                SwipeController(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, adapter)
            )
            swipeHelper.attachToRecyclerView(binding.recyclerViewPosts)
        }
    }

    override fun onItemClicked(post: Post) {
        val intent = Intent(this, MapActivity::class.java)
        intent.putExtra("post", post)
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            LAUNCH_DETAIL_POST_ACTIVITY -> {
                Log.d("TAG", "onActivityResult: result from map activity")
            }
            LAUNCH_NEW_POST_ACTIVITY -> {
                Log.d("TAG", "onActivityResult: result from new post activity")
            }
            else -> {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }
}