package com.example.innovativeproject

// Import necessary libraries for Android, Firebase, and Google Sign-In
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

class SignUp : AppCompatActivity() {
    // Declare variables for user input fields and authentication
    private lateinit var name: String
    private lateinit var phone: String
    private lateinit var email: String
    private lateinit var password: String
    private lateinit var repassword: String
    private lateinit var signg: Button
    private lateinit var signup: Button
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var fauth: FirebaseAuth
    private lateinit var mFirestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        // Initialize UI elements
        signup = findViewById(R.id.submit_button)  // Regular sign-up button
        signg = findViewById(R.id.gsign)  // Google sign-in button
        fauth = Firebase.auth  // Initialize Firebase Authentication
        mFirestore = FirebaseFirestore.getInstance()  // Initialize Firestore database

        // Configure Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.your_web_client_id))  // Request ID token for authentication
            .requestEmail()  // Request email from the user
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)  // Create Google Sign-In client

        // Initialize TextInputLayouts for user input fields
        val objName = findViewById<TextInputLayout>(R.id.name_layout)
        val objPhone = findViewById<TextInputLayout>(R.id.phone_layout)
        val objMail = findViewById<TextInputLayout>(R.id.email_layout)
        val objPass = findViewById<TextInputLayout>(R.id.pass_layout)
        val objRepass = findViewById<TextInputLayout>(R.id.repass_layout)

        // Set click listener for the Sign-Up button
        signup.setOnClickListener {
            // Get user input from text fields
            name = objName.editText?.text.toString()
            phone = objPhone.editText?.text.toString()
            email = objMail.editText?.text.toString()
            password = objPass.editText?.text.toString()
            repassword = objRepass.editText?.text.toString()

            // Reset error messages before validation
            objName.error = ""
            objPhone.error = ""
            objMail.error = ""
            objPass.error = ""
            objRepass.error = ""

            // Validate input fields
            val isValid = errorDetect(objName, objPhone, objMail, objPass, objRepass)
            if (isValid) {
                doSignUpProcess(name, phone, email, password)  // Proceed with sign-up process
            }
        }

        // Set click listener for Google Sign-In button
        signg.setOnClickListener {
            googleSignIn()
        }
    }

    // Function to handle the sign-up process with Firebase Authentication
    private fun doSignUpProcess(name: String, phone: String, email: String, password: String) {
        fauth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this@SignUp) { task ->
                if (task.isSuccessful) {
                    val userId = fauth.uid  // Get user ID from Firebase
                    Log.d("TAG", "doSignUpProcess: $userId")

                    // Create a user details object
                    val userDetails = UserDetails(
                        Email = email,
                        Phoneno = phone,
                        Profile_pic = "",
                        Username = name
                    )
                    writeDataOnFirestore(userDetails, userId!!)  // Save user details in Firestore
                }
            }
            .addOnFailureListener {
                Toast.makeText(this@SignUp, "Account creation failed", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to validate user input fields
    private fun errorDetect(
        objName: TextInputLayout,
        objPhone: TextInputLayout,
        objMail: TextInputLayout,
        objPass: TextInputLayout,
        objRepass: TextInputLayout
    ): Boolean {
        if (name.isEmpty()) {
            objName.error = "Must be filled!"
            return false
        }
        if (phone.isEmpty() || phone.length != 10) {
            objPhone.error = "Must be of 10 digits"
            return false
        }
        if (email.isEmpty()) {
            objMail.error = "Must be filled!"
            return false
        }
        if (password.isEmpty()) {
            objPass.error = "Must be filled!"
            return false
        }
        if (repassword.isEmpty()) {
            objRepass.error = "Must be filled!"
            return false
        }
        if (password != repassword) {
            objPass.error = "Password mismatch!"
            objRepass.error = "Password mismatch!"
            return false
        }
        return true
    }

    // Function to initiate Google Sign-In
    private fun googleSignIn() {
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, 100)
        }
    }


    // Handle Google Sign-In result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100) {  // Check if request code matches Google Sign-In
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!  // Retrieve signed-in account
                firebaseAuthWithGoogle(account.idToken!!)  // Authenticate with Firebase
            } catch (e: ApiException) {
                Log.w("TAG", "Google sign in failed", e)
            }
        }
    }

    // Authenticate with Firebase using Google account credentials
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        fauth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("TAG", "signInWithCredential:success")
                    val user = fauth.currentUser
                    val userEmail = user?.email

                    // Check if the email already exists in Firestore
                    if (userEmail != null) {
                        mFirestore.collection("User_profile")
                            .whereEqualTo("Email", userEmail)
                            .get()
                            .addOnSuccessListener { documents ->
                                if (!documents.isEmpty) {
                                    // Email already exists in Firestore, show error and sign out
                                    Toast.makeText(this, "Google account already exists!", Toast.LENGTH_SHORT).show()
                                    fauth.signOut()
                                    googleSignInClient.signOut() // Ensure the user is logged out from Google
                                } else {
                                    // New user, proceed with registration
                                    val userDetails = UserDetails(
                                        Email = userEmail,
                                        Phoneno = "", // Google Sign-In doesn't provide phone numbers
                                        Profile_pic = user.photoUrl?.toString() ?: "",
                                        Username = user.displayName ?: "User"
                                    )
                                    writeDataOnFirestore(userDetails, user.uid)
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error checking account existence", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Log.w("TAG", "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
                }
            }
    }


    // Function to write user data to Firestore
    private fun writeDataOnFirestore(userDetails: UserDetails, userId: String) {
        val user = hashMapOf(
            "Email" to userDetails.Email,
            "Phoneno" to userDetails.Phoneno,
            "Profile_pic" to userDetails.Profile_pic,
            "User_name" to userDetails.Username
        )

        mFirestore.collection("User_profile").document(userId)
            .set(user)
            .addOnSuccessListener {
                Log.d("TAG", "DocumentSnapshot successfully written!")
                Toast.makeText(this@SignUp, "Account created", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))  // Navigate to main activity
                finish()
            }
            .addOnFailureListener {
                fauth.currentUser?.delete()  // Delete user if Firestore write fails
                Toast.makeText(this@SignUp, "User profile editing failed!", Toast.LENGTH_SHORT).show()
            }
    }
}
