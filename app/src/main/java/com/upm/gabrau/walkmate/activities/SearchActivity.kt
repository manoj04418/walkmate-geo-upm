package com.upm.gabrau.walkmate.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.databinding.ActivitySearchBinding
import com.upm.gabrau.walkmate.firebase.Queries
import com.upm.gabrau.walkmate.models.User
import com.upm.gabrau.walkmate.utils.UserAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity(), UserAdapter.OnItemClickListener {
    private lateinit var binding: ActivitySearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setSupportActionBar(binding.toolbar.root)
        supportActionBar?.title = "Search"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.root.setNavigationOnClickListener { onBackPressed() }

        binding.recyclerViewUsers.layoutManager = LinearLayoutManager(baseContext)
        updateAdapter(arrayListOf())
        initSearchView()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        findViewById<ImageView>(R.id.backpack).visibility = View.GONE
        menu?.findItem(R.id.toolbar_done)?.isVisible = false
        menu?.findItem(R.id.toolbar_logout)?.isVisible = false
        menu?.findItem(R.id.toolbar_search)?.isVisible = false
        return true
    }

    private fun updateAdapter(list: ArrayList<User?>) {
        binding.recyclerViewUsers.adapter = UserAdapter(list, this)
    }

    private fun initSearchView() {
        binding.search.isActivated = true
        binding.search.onActionViewExpanded()
        binding.search.clearFocus()

        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    CoroutineScope(Dispatchers.Main).launch {
                        val users = Queries().getUsersByQuery(it)
                        updateAdapter(users)
                    }
                    return true
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    override fun onItemClicked(user: User) {
        val intent = Intent(this, ProfileActivity::class.java)
        intent.putExtra("uid", user.id)
        startActivity(intent)
    }
}