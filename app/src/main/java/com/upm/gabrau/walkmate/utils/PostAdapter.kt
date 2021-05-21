package com.upm.gabrau.walkmate.utils

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.Style
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.activities.ProfileActivity
import com.upm.gabrau.walkmate.databinding.ItemPostBinding
import com.upm.gabrau.walkmate.firebase.Queries
import com.upm.gabrau.walkmate.models.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class PostAdapter(
    private val context: Context,
    private val postList: ArrayList<Post?>,
    private val itemClickListener: OnItemClickListener
) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        Mapbox.getInstance(context, context.getString(R.string.mapbox_access_token))
        val binding = ItemPostBinding
            .inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun getItemCount() = postList.size

    fun removePost(position: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            postList[position]?.let {
                val success = Queries().removePost(it)
                if (success) {
                    postList.removeAt(position)
                    notifyItemRemoved(position)
                }
                else Toast.makeText(context, "Could not remove post", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        with(holder) {
            with(postList[position]) {
                val post = this
                binding.textViewTitle.text = post?.name
                binding.textViewTitle.isSelected = true

                CoroutineScope(Dispatchers.Main).launch {
                    val user = Queries().getUser(post?.creator)
                    user?.let { u ->
                        binding.textViewUser.text = u.name
                        binding.textViewUser.isSelected = true
                        if (post?.creator != Queries().getCurrentUserId()) {
                            binding.textViewUser.setOnClickListener {
                                val intent = Intent(context, ProfileActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                intent.putExtra("uid", u.id)
                                context.startActivity(intent)
                            }
                        }
                    }
                }

                val c = Calendar.getInstance()
                c.time = post?.created!!
                val year = c.get(Calendar.YEAR).toString().substring(2, 4)
                val date = "Created: ${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH)+1}/$year" +
                        " at ${c.get(Calendar.HOUR_OF_DAY)}:${c.get(Calendar.MINUTE)}"
                binding.textViewCreated.text = date

                post.geoPoint?.let { geo ->
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
                    itemClickListener.onItemClicked(post)
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