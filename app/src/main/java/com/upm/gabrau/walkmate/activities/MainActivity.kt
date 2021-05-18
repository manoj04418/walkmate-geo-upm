package com.upm.gabrau.walkmate.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.GeoPoint
import com.upm.gabrau.walkmate.databinding.ActivityMainBinding
import com.upm.gabrau.walkmate.databinding.ItemPostBinding
import com.upm.gabrau.walkmate.models.Post

private const val LAUNCH_DETAIL_POST_ACTIVITY = 1
private const val LAUNCH_NEW_POST_ACTIVITY = 2

class MainActivity : AppCompatActivity(), PostAdapter.OnItemClickListener {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setSupportActionBar(binding.toolbar.root)

        val posts = arrayOf(
            Post(name = "Gabriel subnormal", geoPoint = GeoPoint(0.0, 0.0)),
            Post(name = "Gabriel subnormal", geoPoint = GeoPoint(0.0, 0.0)),
            Post(name = "Gabriel subnormal", geoPoint = GeoPoint(0.0, 0.0)),
            Post(name = "Gabriel subnormal", geoPoint = GeoPoint(0.0, 0.0)),
            Post(name = "Gabriel subnormal", geoPoint = GeoPoint(0.0, 0.0))
        )

        binding.recyclerViewPosts.adapter = PostAdapter(posts, this)
        binding.recyclerViewPosts.layoutManager = LinearLayoutManager(baseContext)

        binding.fab.setOnClickListener{
            val intent = Intent(this, NewPostActivity::class.java)
            startActivityForResult(intent, LAUNCH_NEW_POST_ACTIVITY)
        }
    }

    override fun onItemClicked(post: Post) {
        val intent = Intent(this, MapActivity::class.java)
        intent.putExtra("post", post)
        startActivityForResult(intent, LAUNCH_DETAIL_POST_ACTIVITY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == LAUNCH_DETAIL_POST_ACTIVITY) {
            Log.d("TAG", "onActivityResult: result from map activity")
        } else if (requestCode == LAUNCH_NEW_POST_ACTIVITY) {
            Log.d("TAG", "onActivityResult: result from new post activity")
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}

class PostAdapter(private val postList: Array<Post>,
                  private val itemClickListener: OnItemClickListener) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun getItemCount() = postList.size

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        with(holder) {
            with(postList[position]) {
                binding.textViewTitle.text = this.name
                binding.textViewLocation.text = this.geoPoint.toString()

                holder.itemView.setOnClickListener{
                    itemClickListener.onItemClicked(this)
                }
            }
        }
    }

    inner class PostViewHolder(val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root)

    interface OnItemClickListener{
        fun onItemClicked(post: Post)
    }
}