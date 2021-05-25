package com.upm.gabrau.walkmate.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import com.upm.gabrau.walkmate.databinding.ActivityLoginBinding
import com.upm.gabrau.walkmate.firebase.Queries
import com.upm.gabrau.walkmate.models.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Matcher
import java.util.regex.Pattern

class LogInActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    /**
     * LiveData object to manage the switching between Login and Create user interfaces
     * */
    private val changedUI: MutableLiveData<Boolean> = MutableLiveData()

    /**
     * This function serves as a disjunction for showing the actual loading screen
     * or navigate to the [MainActivity]. It depends on the actual session of the user:
     *
     * If it is active --> navigate to [MainActivity], no need to log in
     * If it not active --> log in authentication is needed
     * */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Queries().isUserSessionActive()) {
            startActivity(Intent(baseContext, MainActivity::class.java))
        } else {
            binding = ActivityLoginBinding.inflate(layoutInflater)
            val view = binding.root
            setContentView(view)

            changedUI.value = false

            initButton()
            binding.textViewSwitch.setOnClickListener { changedUI.value = !changedUI.value!! }

            changedUI.observe(this, { changed ->
                if (changed) {
                    binding.createUserButton.text = "Log In"
                    binding.layoutName.isVisible = false
                    binding.textViewUsername.isVisible = false
                    binding.textViewSwitch.text = "Register"
                } else {
                    binding.createUserButton.text = "Create User"
                    binding.layoutName.isVisible = true
                    binding.textViewUsername.isVisible = true
                    binding.textViewSwitch.text = "Login"
                }
            })
        }
    }

    /**
     * Manages the CREATE USER - LOG IN button, depending on [changedUI] value.
     * It makes the required validation and then calls signUser or createUser from [Queries]
     * */
    private fun initButton() {
        binding.createUserButton.setOnClickListener {
            changedUI.value?.let {
                if (it) {
                    if (binding.editTextEmail.text?.trim()?.isNotEmpty() == true &&
                        isEmailValid(binding.editTextEmail.text.toString()) &&
                        binding.editTextPassword.text?.trim()?.isNotEmpty() == true) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val success = Queries().signUser(binding.editTextEmail.text.toString(),
                                binding.editTextPassword.text.toString())
                            if (success) {
                                startActivity(Intent(baseContext, MainActivity::class.java))
                            } else {
                                Toast.makeText(baseContext, "Something went wrong", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "You must complete the form", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (binding.editTextName.text?.trim()?.isNotEmpty() == true &&
                        binding.editTextEmail.text?.trim()?.isNotEmpty() == true &&
                        isEmailValid(binding.editTextEmail.text.toString()) &&
                        binding.editTextPassword.text?.trim()?.isNotEmpty() == true) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val user = User(name = binding.editTextName.text.toString())
                            val createdUser = Queries().createUser(binding.editTextEmail.text.toString(),
                                binding.editTextPassword.text.toString(), user)
                            if (createdUser != null) {
                                startActivity(Intent(baseContext, MainActivity::class.java))
                            } else {
                                Toast.makeText(baseContext, "Something went wrong", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "You must complete the form", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun isEmailValid(email: String?): Boolean {
        val expression = "^[\\w\\.-]+@([\\w\\-]+\\.)+[A-Z]{2,4}$"
        val pattern: Pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE)
        val matcher: Matcher = pattern.matcher(email.toString())
        return matcher.matches()
    }
}