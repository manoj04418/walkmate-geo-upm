package com.upm.gabrau.walkmate.utils

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.upm.gabrau.walkmate.databinding.ItemUserBinding
import com.upm.gabrau.walkmate.models.User
import java.util.*

class UserAdapter(private val postList: ArrayList<User?>, private val itemClickListener: OnItemClickListener) :
    RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun getItemCount() = postList.size

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        with(holder) {
            with(postList[position]) {
                binding.username.text = this?.name
            }
        }

        holder.itemView.setOnClickListener{
            postList[position]?.let { itemClickListener.onItemClicked(it) }
        }
    }

    inner class UserViewHolder(val binding: ItemUserBinding) :
        RecyclerView.ViewHolder(binding.root)

    interface OnItemClickListener{
        fun onItemClicked(user: User)
    }
}