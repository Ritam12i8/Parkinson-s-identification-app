package com.example.innovativeproject

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class AfterSignUp : AppCompatActivity() {

    private lateinit var textInput: TextInputEditText
    private lateinit var submitButton: Button
    private lateinit var loggingout: Button
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userId: String
    private lateinit var progressBar: ProgressBar
    private lateinit var refreshButton: Button


    private val holdTimes = mutableListOf<Long>()
    private val pressPressLatencies = mutableListOf<Long>()
    private val releaseReleaseLatencies = mutableListOf<Long>()
    private val flightTimes = mutableListOf<Long>()
    private val afterPunctuationPauseTimes = mutableListOf<Long>() // New list for APP
    private val preCorrectionSlowingTimes = mutableListOf<Long>() // 🆕 Pre-CS list
    private val postCorrectionSlowingTimes = mutableListOf<Long>()  // 🆕 Post-CS list

    private var lastKeyPressTime: Long = 0
    private var lastKeyReleaseTime: Long = 0
    private var previousKeyReleaseTime: Long = 0
    private var correctionDuration: Long = 0
    private var lastInputLength: Int = 0
    private var lastPunctuationTime: Long = 0 // Track last punctuation time
    private var lastNonBackspaceKeyPressTime: Long = 0L
    private var lastBackspaceReleaseTime: Long = 0L  // 🆕 Track last backspace press time
    private var hasWarnedExtraCharacters = false // 🆕 Add this variable at class level



    private val expectedText = "The quick brown fox jumps over the lazy dog near the riverbank. With every leap, it displays a burst of agility and energy, leaving behind a trail of excitement. 1234! What an extraordinary sight to behold! Can it jump over 5 more obstacles before sunset?"

    private var isSubmitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_after_sign_up)

        textInput = findViewById(R.id.text_input)
        submitButton = findViewById(R.id.submit_button)
        loggingout = findViewById(R.id.lout)
        progressBar = findViewById(R.id.progress_bar)
        refreshButton = findViewById(R.id.refresh)


        firestore = FirebaseFirestore.getInstance()
        userId = Firebase.auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 🔒 Disable refresh until typing starts
        refreshButton.isEnabled = false

        textInput.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                lastInputLength = s?.length ?: 0
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {



                val currentTime = System.nanoTime()
                val inputLength = s?.length ?: 0
                val expectedLength = expectedText.length

                // ✅ Calculate capped percentage for the progress bar
                val percentage = ((inputLength.toDouble() / expectedLength) * 100).coerceAtMost(100.0)
                progressBar.progress = percentage.toInt()

// 🚨 Warn only once if extra characters are detected
                if (inputLength > expectedLength && !hasWarnedExtraCharacters) {
                    Toast.makeText(this@AfterSignUp, "⚠️ Extra characters detected. Please delete them.", Toast.LENGTH_SHORT).show()
                    hasWarnedExtraCharacters = true // ✅ Prevent spammy toasts
                } else if (inputLength <= expectedLength) {
                    hasWarnedExtraCharacters = false // 🔄 Reset warning when input is valid
                }

// 🎨 Change progress bar color based on input validity
                progressBar.progressTintList = if (inputLength > expectedLength) {
                    ColorStateList.valueOf(Color.RED) // 🔴 Red if extra characters
                } else {
                    ColorStateList.valueOf(Color.parseColor("#4CAF50")) // 🟢 Green if within limit
                }


                // ✅ Enable refresh when typing starts
                if (!refreshButton.isEnabled && !s.isNullOrEmpty()) {
                    refreshButton.isEnabled = true
                }

                // ✅ Press-Press Latency
                if (lastKeyPressTime != 0L) {
                    val pressPressLatency = (currentTime - lastKeyPressTime) / 1_000_000
                    pressPressLatencies.add(pressPressLatency)
                }

                // ✅ Flight Time (Next Key Press - Previous Key Release)
                if (lastKeyReleaseTime != 0L) {
                    val flightTime = (currentTime - lastKeyReleaseTime) / 1_000_000
                    flightTimes.add(flightTime)
                }

                // After Punctuation Pause Detection
                val currentChar = s?.getOrNull(start + count - 1) ?: return

                // ✅ Record punctuation press time
                if (!currentChar.isLetterOrDigit() && !currentChar.isWhitespace()) {
                    lastPunctuationTime = currentTime
                }

                // ✅ Calculate APP when next alphanumeric key is pressed (ignoring spaces)
                if (currentChar.isLetterOrDigit() && lastPunctuationTime != 0L) {
                    val appTime = (currentTime - lastPunctuationTime) / 1_000_000 // ms
                    afterPunctuationPauseTimes.add(appTime)
                    lastPunctuationTime = 0 // Reset after capturing APP
                }
                // Detect non-backspace key press:
                if (!(before > count)) {
                    lastNonBackspaceKeyPressTime = currentTime  // Update only for non-backspace keys
                }
                // ✅ Detect backspace for Pre-CS and prepare for Post-CS
                if (before > count && lastNonBackspaceKeyPressTime != 0L) {
                    val preCsTime = (currentTime - lastNonBackspaceKeyPressTime) / 1_000_000
                    preCorrectionSlowingTimes.add(preCsTime)
                    lastBackspaceReleaseTime = currentTime // Prepare for Post-CS
                }
                // ✅ Record Post-CS on next alphanumeric key press
                if (currentChar.isLetterOrDigit() && lastBackspaceReleaseTime != 0L) {
                    val postCsTime = (currentTime - lastBackspaceReleaseTime) / 1_000_000 // ✅ Correct formula
                    postCorrectionSlowingTimes.add(postCsTime)
                    lastBackspaceReleaseTime = 0L  // Reset
                }

                lastKeyPressTime = currentTime

            }

            override fun afterTextChanged(s: Editable?) {
                val currentTime = System.nanoTime()

                // ✅ Release-Release Latency (Current Release - Previous Release)
                if (previousKeyReleaseTime != 0L) {
                    val releaseReleaseLatency = (currentTime - previousKeyReleaseTime) / 1_000_000
                    releaseReleaseLatencies.add(releaseReleaseLatency)
                }

                // ✅ Hold Time (Current Release - Last Press)
                if (lastKeyPressTime != 0L) {
                    val holdTime = (currentTime - lastKeyPressTime) / 1_000_000
                    holdTimes.add(holdTime)
                }

                // ✅ Correction Duration (Detect Backspace)
                if (s != null && s.length < lastInputLength) {
                    correctionDuration += (currentTime - lastKeyPressTime) / 1_000_000
                }

                // Check if the last character is a punctuation mark and if the input is not empty
                if (s != null && s.isNotEmpty()) {
                    val lastChar = s[s.length - 1]
                    if (!lastChar.isLetterOrDigit() && lastPunctuationTime != 0L) {
                        // Calculate APP for the last punctuation mark
                        val appTime = (currentTime - lastPunctuationTime) / 1_000_000 // ms
                        afterPunctuationPauseTimes.add(appTime)
                        lastPunctuationTime = 0 // Reset after capturing APP
                    }
                }
                if (s != null && s.length < lastInputLength) {
                    // Backspace detected → capture RELEASE time here
                    lastBackspaceReleaseTime = System.nanoTime()
                    correctionDuration += (currentTime - lastKeyPressTime) / 1_000_000
                }


                // Update release times
                previousKeyReleaseTime = lastKeyReleaseTime
                lastKeyReleaseTime = currentTime
            }
        })


        submitButton.setOnClickListener {
            if (!isSubmitted) {
                ensurePostCsCompletion() // 🆕 Add fallback Post-CS if needed
                val userInput = normalizeText(textInput.text.toString())
                if (userInput == normalizeText(expectedText)) {
                    saveKeystrokeMetricsToFirestore()
                    Toast.makeText(this, "🎉 Great job! You completed the text!", Toast.LENGTH_SHORT).show()
                    isSubmitted = true
                    submitButton.isEnabled = false
                    textInput.isEnabled = false
                } else {
                    Toast.makeText(this, "Please type the text correctly.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "You have already submitted the text.", Toast.LENGTH_SHORT).show()
            }
        }

        refreshButton.setOnClickListener {
            resetPageState()
        }

        loggingout.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Exit")
                .setMessage("Are you sure you want to log out of the application?")
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Yes") { _, _ -> userLoggingOut() }
                .show()
        }
    }

    private fun resetPageState() {
        textInput.text?.clear()
        progressBar.progress = 0
        submitButton.isEnabled = true
        textInput.isEnabled = true
        refreshButton.isEnabled = false
        isSubmitted = false

        // 🧹 Clear all metrics
        holdTimes.clear()
        pressPressLatencies.clear()
        releaseReleaseLatencies.clear()
        flightTimes.clear()
        afterPunctuationPauseTimes.clear()
        preCorrectionSlowingTimes.clear()
        postCorrectionSlowingTimes.clear()

        // 🧹 Reset timing variables
        lastKeyPressTime = 0
        lastKeyReleaseTime = 0
        previousKeyReleaseTime = 0
        correctionDuration = 0
        lastInputLength = 0
        lastPunctuationTime = 0
        lastNonBackspaceKeyPressTime = 0L
        lastBackspaceReleaseTime = 0L
        hasWarnedExtraCharacters = false // ✅ Reset warning flag

        Toast.makeText(this, "Page reset. Start typing again!", Toast.LENGTH_SHORT).show()
    }

    private fun normalizeText(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    }
    private fun ensurePostCsCompletion() {
        if (lastBackspaceReleaseTime != 0L) {
            postCorrectionSlowingTimes.add(0L) // Add fallback only once
            lastBackspaceReleaseTime = 0L
        }
    }

    private fun saveKeystrokeMetricsToFirestore() {
        val currentMetrics = mapOf(
            "HoldTime" to holdTimes,
            "PressPressLatency" to pressPressLatencies,
            "ReleaseReleaseLatency" to releaseReleaseLatencies,
            "FlightTime" to flightTimes,
            "AfterPunctuationPause" to afterPunctuationPauseTimes,
            "PreCorrectionSlowing" to preCorrectionSlowingTimes,
            "PostCorrectionSlowing" to postCorrectionSlowingTimes, // 🆕 Save Post-CS
            "CorrectionDuration" to correctionDuration,
            "AvgHoldTime" to holdTimes.averageOrNull(),
            "AvgPressPressLatency" to pressPressLatencies.averageOrNull(),
            "AvgReleaseReleaseLatency" to releaseReleaseLatencies.averageOrNull(),
            "AvgFlightTime" to flightTimes.averageOrNull(),
            "AvgAfterPunctuationPause" to afterPunctuationPauseTimes.averageOrNull(),
            "AvgPreCorrectionSlowing" to preCorrectionSlowingTimes.averageOrNull(),
            "AvgPostCorrectionSlowing" to postCorrectionSlowingTimes.averageOrNull(), // 🆕 Save Avg Post-CS
            "Timestamp" to getCurrentTimestamp()
        )

        firestore.collection("User_profile").document(userId)
            .get().addOnSuccessListener { document ->
                if (document.exists()) {
                    firestore.collection("User_profile").document(userId)
                        .update("metrics", FieldValue.arrayUnion(currentMetrics))
                } else {
                    val userProfile = hashMapOf("metrics" to arrayListOf(currentMetrics))
                    firestore.collection("User_profile").document(userId).set(userProfile)
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save metrics: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun userLoggingOut() {
        getSharedPreferences("inputsp", Context.MODE_PRIVATE).edit().clear().apply()
        Firebase.auth.signOut()

        // 🚀 Navigate to LoginActivity (replace with your actual login activity name)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clears back stack
        }
        startActivity(intent) // 🔄 Pushes user back to login screen
    }


    private fun List<Long>.averageOrNull(): Double? {
        return if (this.isNotEmpty()) this.average() else 0.0
    }
    // 🆕 ✅ Fallback: Ensure Post-CS is recorded if no further typing occurs
    private fun ensurePostCsOnSubmit() {
        if (lastBackspaceReleaseTime != 0L) {
            // User ended typing after backspace without pressing alphanumeric keys
            postCorrectionSlowingTimes.add(0L) // Or use a fallback value (e.g., average delay)
            lastBackspaceReleaseTime = 0L
        }
    }
}