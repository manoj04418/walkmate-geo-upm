package com.upm.gabrau.walkmate.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.Style
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.databinding.ActivityMainBinding
import com.upm.gabrau.walkmate.databinding.ItemPostBinding
import com.upm.gabrau.walkmate.firebase.Queries
import com.upm.gabrau.walkmate.models.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

private const val LAUNCH_DETAIL_POST_ACTIVITY = 1
private const val LAUNCH_NEW_POST_ACTIVITY = 2

class MainActivity : AppCompatActivity(), PostAdapter.OnItemClickListener {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        val activity = this

        setSupportActionBar(binding.toolbar.root)

        CoroutineScope(Dispatchers.Main).launch {
            val p = Queries().getUserPosts()
            p?.let {
                // TODO: Remove post on Swipe
                binding.recyclerViewPosts.adapter = PostAdapter(baseContext, it, activity)
                binding.recyclerViewPosts.layoutManager = LinearLayoutManager(baseContext)
            }
        }

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
        val activity = this
        when (requestCode) {
            LAUNCH_DETAIL_POST_ACTIVITY -> {
                Log.d("TAG", "onActivityResult: result from map activity")
            }
            LAUNCH_NEW_POST_ACTIVITY -> {
                Log.d("TAG", "onActivityResult: result from new post activity")
                CoroutineScope(Dispatchers.Main).launch {
                    val p = Queries().getUserPosts()
                    p?.let {
                        binding.recyclerViewPosts.adapter = PostAdapter(baseContext, it, activity)
                    }
                }
            }
            else -> {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }
}

class PostAdapter(
    private val context: Context,
    private val postList: ArrayList<Post?>,
    private val itemClickListener: OnItemClickListener) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        Mapbox.getInstance(context, context.getString(R.string.mapbox_access_token))
        val binding = ItemPostBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun getItemCount() = postList.size

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        with(holder) {
            with(postList[position]) {
                binding.textViewTitle.text = this?.name
                binding.textViewTitle.isSelected = true

                val c = Calendar.getInstance()
                c.time = this?.created!!
                val year = c.get(Calendar.YEAR).toString().substring(2, 4)
                val date = "Added: ${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH)+1}/$year"
                binding.textViewCreated.text = date

                this.geoPoint?.let { geo ->
                    val latLng = LatLng(geo.latitude,geo.longitude)
                    binding.postMap.getMapAsync { mapboxMap ->
                        val uiSettings = mapboxMap.uiSettings
                        uiSettings.setAllGesturesEnabled(false)
                        mapboxMap.cameraPosition = CameraPosition.Builder()
                            .zoom(15.0).target(latLng)
                            .build()
                        mapboxMap.setStyle(Style.OUTDOORS)
                    }
                }

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