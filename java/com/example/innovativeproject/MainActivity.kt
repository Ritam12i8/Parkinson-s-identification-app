package com.example.innovativeproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private lateinit var fauth: FirebaseAuth
    private lateinit var googleSignInClient: SignInClient
    private lateinit var oneTapSignInRequest: BeginSignInRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        fauth = Firebase.auth

        // Check if the user is already logged in
        if (fauth.currentUser != null) {
            startActivity(Intent(this, AfterSignUp::class.java))
            finish()
            return
        }

        // Set the UI
        setContentView(R.layout.activity_main)

        val btnSignUp = findViewById<Button>(R.id.Sign_up)
        val btnGoogleSignIn = findViewById<Button>(R.id.submit_withG)

        val objEmail = findViewById<TextInputLayout>(R.id.email_layout)
        val objPass = findViewById<TextInputLayout>(R.id.pass_layout)

        findViewById<Button>(R.id.submit_button).setOnClickListener {
            val mail = objEmail.editText?.text.toString()
            val pass = objPass.editText?.text.toString()

            if (mail.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
            } else {
                doLoginProcess(mail, pass)
            }
        }

        btnSignUp.setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
        }

        // Initialize Google Sign-In
        googleSignInClient = Identity.getSignInClient(this)

        oneTapSignInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.your_web_client_id)) // Replace with your Web Client ID
                    .setFilterByAuthorizedAccounts(true)
                    .build()
            )
            .build()

        btnGoogleSignIn.setOnClickListener {
            googleSignIn()
        }
    }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                try {
                    val credential = googleSignInClient.getSignInCredentialFromIntent(result.data)
                    val idToken = credential.googleIdToken
                    if (idToken != null) {
                        firebaseAuthWithGoogle(idToken)
                    }
                } catch (e: ApiException) {
                    Log.e("GoogleSignIn", "Google sign in failed", e)
                    Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun googleSignIn() {
        googleSignInClient.beginSignIn(oneTapSignInRequest)
            .addOnSuccessListener { result ->
                googleSignInLauncher.launch(
                    IntentSenderRequest.Builder(result.pendingIntent).build()
                )
            }
            .addOnFailureListener { e ->
                Log.e("GoogleSignIn", "Google Sign-In failed: ${e.message}")
                Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        fauth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, AfterSignUp::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun doLoginProcess(mail: String, pass: String) {
        fauth.signInWithEmailAndPassword(mail, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = fauth.currentUser
                    Log.d("TAG", "doLoginProcess: ${user?.uid}")
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, AfterSignUp::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unknown error occurred", Toast.LENGTH_SHORT).show()
            }
    }
}
