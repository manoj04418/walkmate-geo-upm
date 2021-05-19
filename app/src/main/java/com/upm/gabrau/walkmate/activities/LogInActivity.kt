package com.upm.gabrau.walkmate.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Queries().isUserSessionActive()) {
            startActivity(Intent(baseContext, MainActivity::class.java))
        } else {
            binding = ActivityLoginBinding.inflate(layoutInflater)
            val view = binding.root
            setContentView(view)

            val activity = this

            binding.createUserButton.setOnClickListener {
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
                            Toast.makeText(baseContext, "Algo ha salido mal", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Debes completar el nombre de usuario", Toast.LENGTH_SHORT).show()
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