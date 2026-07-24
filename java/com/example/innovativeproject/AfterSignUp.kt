package com.example.innovativeproject

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.innovativeproject.databinding.ActivityAfterSignUpBinding // Import the generated binding class
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import kotlin.math.sqrt
import android.util.DisplayMetrics
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager // We'll use this for wrapping text
import android.view.LayoutInflater // Needed for RecyclerView adapter
import android.view.ViewGroup // Needed for RecyclerView adapter
import android.graphics.Typeface // To potentially bold current character
import android.widget.TextView
import android.graphics.Paint // Import this
import android.util.Log
import android.util.TypedValue // Import this


// Data class to represent each character in the expected text
data class CharacterItem(
    val char: Char,
    var isCorrect: Boolean? = null, // null: not yet typed, true: correct, false: incorrect
    var isCurrent: Boolean = false // true: is the character the user is currently on
)

class AfterSignUp : AppCompatActivity() {

// UI elements, now accessed via View Binding
    private lateinit var textInput: TextInputEditText
    private lateinit var submitButton: Button
    private lateinit var loggingOutButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var refreshButton: Button
    private lateinit var binding: ActivityAfterSignUpBinding // Declare the binding object

    // Firebase related variables
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userId: String

    // UI elements, now accessed via View Binding
    private lateinit var characterRecyclerView: RecyclerView // NEW: RecyclerView for characters
    private lateinit var characterAdapter: CharacterAdapter // NEW: Adapter for the RecyclerView


    // Keystroke metric lists: These lists store the raw timing data for various keystroke features.
// They are mutable as data is added during typing.
    private val holdTimes = mutableListOf<Double>() // Duration a key is pressed down
    private val pressPressLatencies = mutableListOf<Double>() // Time between consecutive key presses
    private val releaseReleaseLatencies = mutableListOf<Double>() // Time between consecutive key releases
    private val flightTimes = mutableListOf<Double>() // Time between key release and subsequent key press (inter-key interval)
    private val afterPunctuationPauseTimes = mutableListOf<Double>() // Pause duration after typing a punctuation and before the next character
    private val preCorrectionSlowingTimes = mutableListOf<Double>() // Slowing down before a backspace/correction
    private val postCorrectionSlowingTimes = mutableListOf<Double>() // Slowing down after a backspace/correction
    private val keyPressTimestamps = mutableListOf<Pair<Char, Long>>() // Stores character and its press timestamp for bounce detection
    private val typingSpeedOverTime = mutableListOf<Double>() // Characters per second (CPS) captured at intervals
    private val speedCaptureIntervalMs = 5000L // Interval (in milliseconds) at which typing speed is calculated



// Constant expected text string: The predefined text the user is required to type.
    private val expectedText ="The quick brown fox jumps over the lazy dog near the riverbank. With every leap, it displays a burst of agility and energy, leaving behind a trail of excitement. 1234! What an extraordinary sight to behold! Can it jump over 5 more obstacles before sunset?"



// Timing and state variables: These variables keep track of the current state of typing and crucial timestamps.

    private var lastKeyPressTime: Long = 0 // Timestamp of the last key press
    private var lastKeyReleaseTime: Long = 0 // Timestamp of the last key release
    private var previousKeyReleaseTime: Long = 0 // Timestamp of the second to last key release (for release-release latency)
    private var correctionDuration: Long = 0 // Total duration spent on corrections (backspaces)
    private var lastInputLength: Int = 0 // Length of the input text before the current change (used to detect backspace)
    private var lastPunctuationTime: Long = 0 // Timestamp of the last typed punctuation mark
    private var lastNonBackspaceKeyPressTime: Long = 0L // Timestamp of the last non-backspace key press
    private var lastBackspaceReleaseTime: Long = 0L // Timestamp of the last backspace key release
    private var hasWarnedExtraCharacters: Boolean = false // Flag to ensure extra character warning is shown only once
    private var keyBounceCount: Int = 0 // Counter for detected key bounces
    private var keyBounceFrequency: Double = 0.0 // Key bounce frequency (bounces per second)
    private var startTime: Long = 0L // Timestamp when the user started typing the first character
    private var lastSpeedCaptureTime: Long = 0L // Timestamp of the last typing speed capture
    private var lastCapturedCharCount: Int = 0 // Number of characters captured at the last speed interval
    private var currentPunctuationIndex: Int = -1 // Index of the last punctuation character (not strictly used, can be removed if not needed)
    private var isSubmitted: Boolean = false // Flag to prevent multiple submissions
    private var typedCharacters: MutableList<CharacterItem> = mutableListOf() // NEW: Mutable list to track char state
    private var realTimeTypingErrorCount = 0

    // Device metrics
    private var screenDiagonalInInches: Double = 0.0 // Calculated diagonal screen size in inches


    // NEW: Adapter for the character RecyclerView
    inner class CharacterAdapter(private val characterList: List<CharacterItem>) :
        RecyclerView.Adapter<CharacterAdapter.CharacterViewHolder>() {

        inner class CharacterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val characterTextView: TextView = itemView.findViewById(R.id.characterTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_character_display, parent, false)
            return CharacterViewHolder(view)
        }

        override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
            val characterItem = characterList[position]
            holder.characterTextView.text = characterItem.char.toString()

            // Set background and text color based on state
            when (characterItem.isCorrect) {
                true -> {
                    holder.characterTextView.setBackgroundColor(Color.parseColor("#E0FFE0")) // Light Green
                    holder.characterTextView.setTextColor(Color.BLACK)
                }
                false -> {
                    holder.characterTextView.setBackgroundColor(Color.parseColor("#FFE0E0")) // Light Red
                    holder.characterTextView.setTextColor(Color.BLACK)
                }
                null -> {
                    holder.characterTextView.setBackgroundColor(Color.TRANSPARENT) // Default
                    holder.characterTextView.setTextColor(Color.BLACK)
                }
            }

            // Highlight the current character (bold, underline, or distinct background)
            if (characterItem.isCurrent) {
                holder.characterTextView.setTypeface(null, Typeface.BOLD)
                // You can add more styling here, e.g., an outline or different background color
                holder.characterTextView.setBackgroundColor(Color.parseColor("#ADD8E6")) // Light Blue for current
            } else {
                holder.characterTextView.setTypeface(null, Typeface.NORMAL)
                // Ensure non-current character's background reverts if it was blue
                if (characterItem.isCorrect == null) { // Only reset if not already correct/incorrect
                    holder.characterTextView.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }

        override fun getItemCount(): Int = characterList.size
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeDeviceMetrics() // Calculate and store device screen metrics

// Initialize View Binding: Inflates the layout and provides access to views.
        binding = ActivityAfterSignUpBinding.inflate(layoutInflater)
        setContentView(binding.root) // Set the activity's content view to the root of the binding layout


// Initialize UI elements using View Binding
        textInput = binding.textInput
        submitButton = binding.submitButton
        loggingOutButton = binding.lout
        progressBar = binding.progressBar
        refreshButton = binding.refresh
        characterRecyclerView = binding.characterRecyclerView // NEW: Initialize RecyclerView

        // Initialize CharacterList and Adapter
        typedCharacters = expectedText.map { CharacterItem(it) }.toMutableList()
        characterAdapter = CharacterAdapter(typedCharacters) // Use the new mutable list here
        characterRecyclerView.adapter = characterAdapter

        // Use GridLayoutManager for a wrapping effect, like text flows
        // We will set the spanCount dynamically after the RecyclerView is measured.
        // For initial setup, create a GridLayoutManager with a placeholder spanCount (e.g., 1)
        // It will be updated correctly in the post {} block below.
        characterRecyclerView.layoutManager = GridLayoutManager(this, 1) // Initial placeholder spanCount

// Disable default keyboard: Prevents the system keyboard from appearing.
        textInput.showSoftInputOnFocus = false


        // *** IMPORTANT CHANGES FOR SCROLLING BEHAVIOR AND FOCUS ***
        textInput.setOnClickListener { // Keep this to show your custom keyboard on tap
            textInput.requestFocus() // Ensure TextInputEditText has focus to receive input
            showCustomKeyboard() // Show your custom keyboard
        }

        // NEW: Add a GlobalLayoutListener to detect keyboard visibility and scroll to top.
        // This ensures the reference text is always visible when the keyboard is up.
        val rootView = binding.root // This is the root view of your activity's layout
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val screenHeight = rootView.height // Total height of the root view
            val rect = Rect()
            // Get the visible portion of the window (excluding system bars and keyboard)
            rootView.getWindowVisibleDisplayFrame(rect)

            val keyboardHeight = screenHeight - rect.bottom // Calculate height occupied by keyboard
            // Define a threshold to differentiate actual keyboard from minor layout changes
            val keyboardThreshold = 150 * resources.displayMetrics.density // e.g., 150dp minimum for a keyboard

            if (keyboardHeight > keyboardThreshold) {
                // Keyboard is likely open, so scroll the content to the very top
                // to ensure the reference text is visible.
                // IMPORTANT: Ensure binding.scrollView refers to your actual ScrollView wrapping the content.
                binding.scrollView.post {
                    binding.scrollView.scrollTo(0, 0)
                }
            }
        }
        // *** END IMPORTANT CHANGES ***


        // NEW: Dynamic spanCount calculation after RecyclerView is laid out
        characterRecyclerView.post {
            val recyclerViewWidth = characterRecyclerView.width

            if (recyclerViewWidth > 0) {
                val paint = Paint()
                paint.textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    15f, // MATCH THIS TO your TextView's textSize in XML (15sp)
                    resources.displayMetrics
                )

                // Measure a space character for a more average width calculation.
                val actualCharTextWidth = paint.measureText(" ")

                // This should be a very small buffer, or 0 if you want no extra space.
                // Aim for 0dp-2dp. 11dp was way too large.
                val horizontalExtraSpacePx = (9 * resources.displayMetrics.density).toInt() // Adjusted from 11dp to 1dp

                // The effective width each character item will occupy in the grid
                val effectiveItemWidth = actualCharTextWidth + horizontalExtraSpacePx

                // Calculate the spanCount based on RecyclerView's width and effective item width
                val calculatedSpanCount = (recyclerViewWidth / effectiveItemWidth).toInt().coerceAtLeast(1)

                // Update the spanCount of the GridLayoutManager
                (characterRecyclerView.layoutManager as? GridLayoutManager)?.spanCount = calculatedSpanCount

            } else {
                // Fallback for extremely rare cases where RecyclerView width isn't available
                // This fallback should also use a smaller value consistent with 15sp text.
                val fallbackEstimatedCharWidthPx = (8 * resources.displayMetrics.density).toInt() // Adjusted for 15sp text
                val fallbackSpanCount = resources.displayMetrics.widthPixels / fallbackEstimatedCharWidthPx
                (characterRecyclerView.layoutManager as? GridLayoutManager)?.spanCount = fallbackSpanCount.coerceAtLeast(1)
            }
        }
        // END NEW DYNAMIC SPAN COUNT CALCULATION


// Disable copy-paste: Prevents users from pasting the expected text.
        disableCopyPaste()

// Initialize Firebase Firestore and get current user ID
        firestore = FirebaseFirestore.getInstance()
        userId = Firebase.auth.currentUser?.uid ?: run {

// Handle case where user is not authenticated: Show a toast and close the activity.
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return // Exit onCreate early if user ID is null
        }

// Disable refresh button initially until typing starts
        refreshButton.isEnabled = false


// Attach TextWatcher to monitor text input changes
        textInput.addTextChangedListener(object : TextWatcher {

// Called before text is changed. Used to capture the input length before modification.
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                lastInputLength = s?.length ?: 0
            }

// Called when text is changing. This is where most keystroke metrics are captured.
override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

    // If text is null or empty, reset everything
    if (s.isNullOrEmpty()) {
        typedCharacters.forEach {
            it.isCorrect = null
            it.isCurrent = false
        }
        characterAdapter.notifyDataSetChanged()
        updateProgressBar(0, expectedText.length) // Reset progress bar
        refreshButton.isEnabled = false // Disable refresh again if text is empty
        //    Log.d("MetricsTrace", "Input is null or empty. Metrics reset.")
        realTimeTypingErrorCount = 0 // IMPORTANT: Reset error count on empty input
        return
    }

    val currentTime = System.nanoTime() // Current high-resolution time for precise measurements
    // val currentChar = s.getOrNull(start + count - 1) ?: return // This is calculated and used correctly within the loop, no need here.
    val inputLength = s.length // Current length of the input text
    val expectedLength = expectedText.length // Length of the expected text
    val currentTimeMs = currentTime / 1_000_000 // Current time in milliseconds for speed calculation

    // Set typing start time: Initialized only once when the first character is typed.
    if (startTime == 0L) {
        startTime = currentTime // Use nanoTime for overall duration
        lastSpeedCaptureTime = currentTimeMs // Initialize for speed capture
        lastCapturedCharCount = 0 // Initialize char count for speed capture
    }

    // Update progress bar: Shows how much of the expected text has been typed.
    // Red if over-typed, green otherwise.
    updateProgressBar(inputLength, expectedLength)

    // Warn about extra characters: Notifies user if they've typed beyond the expected length.
    checkExtraCharactersWarning(inputLength, expectedLength)

    // Enable refresh button: Once typing begins, the refresh button becomes active.
    if (!refreshButton.isEnabled) refreshButton.isEnabled = true

    // --- Core Character Tracking and Mismatch Detection Logic ---

    // Reset current state for all characters first for a clean update
    for (i in 0 until typedCharacters.size) {
        if (typedCharacters[i].isCurrent) {
            typedCharacters[i].isCurrent = false
            characterAdapter.notifyItemChanged(i) // Only notify if it changed
        }
    }

    // Reset error count before re-evaluating, assuming `isCorrect` indicates state
    // If you want a cumulative count, DO NOT reset realTimeTypingErrorCount here.
    // Based on your logs, the Total Errors seems cumulative.
    // I will assume it's cumulative and increment only on *new* mismatches.

    // This variable will hold the number of *current* mismatches in the displayed text.
    // This is useful for knowing the total mismatches currently visible to the user.
    var currentDisplayedMismatches = 0

    val newCurrentIndex = inputLength - 1

    for (i in 0 until inputLength) {
        typedCharacters.getOrNull(i)?.let { charItem ->
            val typedChar = s[i] // Character typed at the current position 'i'

            if (i < expectedText.length) {
                val expectedChar = expectedText[i] // Expected character at the current position 'i'

                if (typedChar == expectedChar) {
                    // Character is correct
                    if (charItem.isCorrect == false) { // If it was previously incorrect, mark it correct
                        charItem.isCorrect = true
                        characterAdapter.notifyItemChanged(i)
                    } else if (charItem.isCorrect == null) { // If it was initially null, mark it correct
                        charItem.isCorrect = true
                        characterAdapter.notifyItemChanged(i)
                    }
                    // No change needed if already correct
                } else {
                    // Character is incorrect (mismatch)
                    if (charItem.isCorrect != false) { // Only if it wasn't already marked false
                        Toast.makeText(this@AfterSignUp, "❌ Character mismatch found! Please check your input.", Toast.LENGTH_SHORT).show()
                        charItem.isCorrect = false
                        characterAdapter.notifyItemChanged(i)
                        // This is the critical line that was missing or in the wrong place!
                        // Increment realTimeTypingErrorCount only for *new* mismatches.
                        // If a user corrects a mistake, the count might not decrease.
                        // If you want to count *every* single mismatch attempt, regardless of correction,
                        // this is where you'd increment it.
                        // This matches your initial log "Total Errors: 174" indicating a cumulative count.
                        realTimeTypingErrorCount++ // Increment the cumulative error count
                        Log.d("TypingMetrics", "ERROR (Mismatch): Pos=$i, Typed='$typedChar', Expected='$expectedChar'. Total Errors: $realTimeTypingErrorCount")
                    }
                    currentDisplayedMismatches++ // Count current visible mismatches
                }
            } else {
                // Typed characters beyond the expected text length are also considered errors
                if (charItem.isCorrect != false) {
                    charItem.isCorrect = false
                    characterAdapter.notifyItemChanged(i)
                    realTimeTypingErrorCount++ // Increment error for extra characters
                    Log.d("TypingMetrics", "ERROR (Extra Char): Pos=$i, Typed='$typedChar', Expected='N/A'. Total Errors: $realTimeTypingErrorCount")
                }
                currentDisplayedMismatches++ // Count current visible mismatches
            }

            // Set the current character for highlighting
            if (i == newCurrentIndex) {
                if (!charItem.isCurrent) {
                    charItem.isCurrent = true
                    characterAdapter.notifyItemChanged(i)
                }
            }
        }
    }

    // Handle characters that are no longer typed (e.g., user backspaced)
    for (i in inputLength until typedCharacters.size) {
        typedCharacters.getOrNull(i)?.let { charItem ->
            if (charItem.isCorrect != null || charItem.isCurrent) {
                charItem.isCorrect = null // Reset to neutral state
                charItem.isCurrent = false
                characterAdapter.notifyItemChanged(i)
            }
        }
    }


    // Update keystroke metrics on key press: Consolidates all metric calculations related to a key press.
    // The currentChar was previously derived as s.getOrNull(start + count - 1),
    // which is essentially the character at newCurrentIndex.
    // If you need the *specific* character just typed for metrics, this is fine.
    // If updateMetricsOnKeyPress also has its own error logic, you need to consolidate.
    val charJustTyped = s.getOrNull(newCurrentIndex)
    if (charJustTyped != null) {
        updateMetricsOnKeyPress(currentTime, charJustTyped, start, count, before, inputLength, currentTimeMs)
    }

    // Update last key press time: Important for calculating subsequent latencies.
    lastKeyPressTime = currentTime

    // Final check for completion (if all expected text is typed)
    if (inputLength == expectedLength && currentDisplayedMismatches == 0) {
        // All text typed and no current mismatches, consider it finished.
        // You can add your completion logic here, e.g., show results.
        Log.d("TypingMetrics", "All expected text typed correctly! Final Errors: $realTimeTypingErrorCount")
        // Optionally, remove the TextWatcher
        // binding.editTextUserInput.removeTextChangedListener(this) // This assumes you have binding
    }
}

// Called after text has changed. Primarily used for hold times and release-based metrics.
            override fun afterTextChanged(s: Editable?) {
                val currentTime = System.nanoTime() // Current high-resolution time
                val inputLength = s?.length ?: 0 // Current length of the input text
                val wasBackspace = inputLength < lastInputLength // Check if the change was a backspace

// Update last input length: Crucial for detecting backspaces in the next `onTextChanged` call.
                lastInputLength = inputLength

// Handle backspace specific logic: Corrects punctuation tracking and captures correction duration.
                if (wasBackspace) {
                    handleBackspaceDuringTextChange(currentTime)
                }

// Update keystroke metrics on key release: Consolidates all metric calculations related to a key release.
                updateMetricsOnKeyRelease(currentTime, s) // Pass Editable s for lastChar check
            }
        })

// Submit button click listener
        submitButton.setOnClickListener {
// Only allow submission if not already submitted
            if (!isSubmitted) {
                ensurePostCsCompletion() // Add fallback Post-CS if user submits right after a backspace.
                val userInput = normalizeText(textInput.text.toString()) // Normalize user input

// Compare normalized user input with normalized expected text for accuracy.
                if (userInput == normalizeText(expectedText)) {
                    saveKeystrokeMetricsToFirestore() // Save collected metrics to Firestore
                    Toast.makeText(this, "🎉 Great job! You completed the text!", Toast.LENGTH_SHORT).show()
                    isSubmitted = true // Mark as submitted
                    submitButton.isEnabled = false // Disable submit button
                    textInput.isEnabled = false // Disable text input to prevent further typing
                } else {
                    Toast.makeText(this, "Please type the text correctly.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "You have already submitted the text.", Toast.LENGTH_SHORT).show()
            }
        }

// Refresh button click listener: Resets the typing interface and metrics.
        refreshButton.setOnClickListener {
            resetPageState()
        }

// Logout button click listener: Shows a confirmation dialog before logging out.
        loggingOutButton.setOnClickListener {
            showLogoutDialog() // Calls the extracted function for logout confirmation
        }
    }

// --- Extracted Functions for Better Readability and Modularity ---
    /**
     * Updates the progress bar's progress and tint color based on input length relative to expected length.
     * @param inputLength The current length of the user's input.
     * @param expectedLength The length of the expected text.
     */
    private fun updateProgressBar(inputLength: Int, expectedLength: Int) {
        val percentage = ((inputLength.toDouble() / expectedLength) * 100).coerceAtMost(100.0)
        progressBar.progress = percentage.toInt()
        progressBar.progressTintList = if (inputLength > expectedLength)
            ColorStateList.valueOf(Color.RED) // Red if user typed more than expected
        else
            ColorStateList.valueOf(Color.parseColor("#4CAF50")) // Green for correct length or less
    }

    /**
     * Checks if extra characters have been typed and displays a warning toast once.
     * Resets the warning flag if input length falls back within expected bounds.
     * @param inputLength The current length of the user's input.
     * @param expectedLength The length of the expected text.
     */
    private fun checkExtraCharactersWarning(inputLength: Int, expectedLength: Int) {
        if (inputLength > expectedLength && !hasWarnedExtraCharacters) {
            Toast.makeText(this@AfterSignUp, "⚠️ Extra characters detected. Please delete them.", Toast.LENGTH_SHORT).show()
            hasWarnedExtraCharacters = true // Set flag to prevent repeated toasts
        } else if (inputLength <= expectedLength) {
            hasWarnedExtraCharacters = false // Reset flag if user corrects
        }
    }

    /**
     * Consolidates all keystroke metric calculations that occur during the `onTextChanged` event.
     * @param currentTime The current high-resolution timestamp (nanoTime).
     * @param currentChar The character that was just typed.
     * @param start The starting index of the changed text.
     * @param count The number of characters added.
     * @param before The number of characters removed.
     * @param inputLength The current total length of the input text.
     * @param currentTimeMs The current timestamp in milliseconds.
     */
    private fun updateMetricsOnKeyPress(
        currentTime: Long,
        currentChar: Char, // Keep this, might be useful for other metrics
        start: Int, // Keep this, might be useful for other metrics related to position of change
        count: Int, // Keep this, indicates characters added
        before: Int, // Keep this, indicates characters removed (backspace)
        inputLength: Int, // Keep this, current total length
        currentTimeMs: Long
    ) {

        // Calculate Press-Press Latency: Time between this key press and the last one.
        if (lastKeyPressTime != 0L) {
            val latency = (currentTime - lastKeyPressTime) / 1_000_000 // Convert nanoseconds to milliseconds
            pressPressLatencies.add(latency.toDouble())
        }

        // Calculate Flight Time: Time between the last key release and this key press.
        if (lastKeyReleaseTime != 0L) {
            val flightTime = (currentTime - lastKeyReleaseTime) / 1_000_000
            flightTimes.add(flightTime.toDouble())
        }

        val wasBackspace = before > count // Determine if the change was a backspace

        // --- REMOVED THE ERROR CHECKING LOGIC FROM HERE ---
        // The realTimeTypingErrorCount incrementation and associated logging
        // are now solely handled in the onTextChanged method's character loop.
        // This function focuses purely on performance metrics.
        // --- END REMOVED LOGIC ---


        // Handle Punctuation and After-Punctuation Pause (APP)
        if (wasBackspace) {
            // If backspace, reset punctuation tracking as the context changed.
            lastPunctuationTime = 0L
            currentPunctuationIndex = -1 // Not strictly used, can be removed if consistently unused.
        } else if (!currentChar.isLetterOrDigit() && !currentChar.isWhitespace()) {
            // If the character is a punctuation, record its time.
            lastPunctuationTime = currentTime
            currentPunctuationIndex = start + count - 1 // Not strictly used.
        } else if (currentChar.isLetterOrDigit() && lastPunctuationTime != 0L) {
            // If an alphanumeric character is typed after a punctuation, calculate APP.
            val appTime = (currentTime - lastPunctuationTime) / 1_000_000
            afterPunctuationPauseTimes.add(appTime.toDouble())
            lastPunctuationTime = 0L // Reset after calculation
            currentPunctuationIndex = -1 // Reset after calculation
        }

        // Calculate Pre-Correction Slowing (Pre-CS): Time leading up to a backspace.
        if (!wasBackspace) {
            // If not a backspace, update the last non-backspace key press time.
            lastNonBackspaceKeyPressTime = currentTime
        } else if (lastNonBackspaceKeyPressTime != 0L) {
            // If it IS a backspace and a non-backspace press was recorded, calculate Pre-CS.
            val preCsTime = (currentTime - lastNonBackspaceKeyPressTime) / 1_000_000
            preCorrectionSlowingTimes.add(preCsTime.toDouble())

            lastBackspaceReleaseTime = currentTime // Mark this for potential Post-CS
        }

        // Calculate Post-Correction Slowing (Post-CS): Time after a backspace until the next alphanumeric key.
        if (currentChar.isLetterOrDigit() && lastBackspaceReleaseTime != 0L) {
            val postCsTime = (currentTime - lastBackspaceReleaseTime) / 1_000_000
            postCorrectionSlowingTimes.add(postCsTime.toDouble())
            lastBackspaceReleaseTime = 0L // Reset after calculation
        }

        // Key Bounce Detection: Detects if the same key is pressed again too quickly.
        if (keyPressTimestamps.isNotEmpty()) {
            val (lastChar, lastTime) = keyPressTimestamps.last()
            val timeDiffMs = (currentTime - lastTime) / 1_000_000

            // If same character within a specific time range (50-333ms), consider it a bounce.
            if (currentChar == lastChar && timeDiffMs in 50..333) {
                keyBounceCount++
            }
        }

        // Store the current character and its press timestamp for future bounce detection.
        keyPressTimestamps.add(currentChar to currentTime) // Using `to` for Pair creation

        // Key Bounce Frequency (KBF) calculation: Bounces per second of total typing time.
        val totalTypingDuration = currentTime - startTime // Total duration from first key press
        keyBounceFrequency = if (totalTypingDuration > 0)
            keyBounceCount / (totalTypingDuration / 1_000_000_000.0) // Convert total duration to seconds
        else 0.0

        // Characters Per Second (CPS) capture: Calculates typing speed over defined intervals.
        if (currentTimeMs - lastSpeedCaptureTime >= speedCaptureIntervalMs) {
            val charsTyped = inputLength - lastCapturedCharCount // Characters typed since last capture
            val elapsedSec = (currentTimeMs - lastSpeedCaptureTime) / 1000.0 // Elapsed time in seconds

            // Only add if meaningful data exists (chars typed and elapsed time are positive)
            if (elapsedSec > 0 && charsTyped > 0) {
                typingSpeedOverTime.add(charsTyped / elapsedSec)
            }
            lastCapturedCharCount = inputLength // Reset count for next interval
            lastSpeedCaptureTime = currentTimeMs // Reset time for next interval
        }
    }


    /**
     * Handles specific logic when a backspace is detected during `afterTextChanged`.
     * This includes resetting punctuation tracking and contributing to correction duration.
     * @param currentTime The current high-resolution timestamp (nanoTime).
     */
    private fun handleBackspaceDuringTextChange(currentTime: Long) {
// Reset punctuation tracking immediately when a backspace occurs.
        lastPunctuationTime = 0L
        currentPunctuationIndex = -1 // Not strictly used.

// Capture correction duration: Time between the last press and this backspace.
        if (lastKeyPressTime != 0L) {
            val correctionTime = (currentTime - lastKeyPressTime) / 1_000_000
            correctionDuration += correctionTime // Accumulate total correction time
        }

// Mark release time for potential Post-CS tracking: A backspace release might precede a new character.
        lastBackspaceReleaseTime = currentTime
    }


    /**
    * Consolidates all keystroke metric calculations that occur during the `afterTextChanged` event,
     * primarily focusing on release-based metrics like hold times and release latencies.
     * @param currentTime The current high-resolution timestamp (nanoTime).
     * @param s The current Editable text.
     */
    private fun updateMetricsOnKeyRelease(currentTime: Long, s: Editable?) {

// Calculate Release-Release Latency: Time between this key release and the previous one.
        if (previousKeyReleaseTime != 0L) {
            val releaseLatency = (currentTime - previousKeyReleaseTime) / 1_000_000
            releaseReleaseLatencies.add(releaseLatency.toDouble())
        }

// Calculate Hold Time (Press to Release): Duration the key was held down.
        if (lastKeyPressTime != 0L) {
            val holdTime = (currentTime - lastKeyPressTime) / 1_000_000
            holdTimes.add(holdTime.toDouble())
        }



// Note: The After-Punctuation Pause (APP) logic was previously duplicated or unclear.
// The `onTextChanged` APP logic (when an alphanumeric char FOLLOWS punctuation) is more robust.
// This `afterTextChanged` APP logic below is potentially redundant or for a different specific edge case (punctuation release).
// It should be carefully reviewed if it's truly needed or if the `onTextChanged` logic is sufficient.
// For now, it's kept as per your original code structure.
        val lastChar = s?.lastOrNull()
        if (lastChar != null && !lastChar.isLetterOrDigit() && lastPunctuationTime != 0L) {
            val appTime = (currentTime - lastPunctuationTime) / 1_000_000
            afterPunctuationPauseTimes.add(appTime.toDouble())
            lastPunctuationTime = 0L // Reset after calculation
        }

// Update release tracking times for subsequent calculations.
        previousKeyReleaseTime = lastKeyReleaseTime
        lastKeyReleaseTime = currentTime
    }



    /**
     * Clears all collected keystroke metrics and resets all timing and state variables.
     * This prepares the activity for a new typing session.
     */
    private fun clearAllMetrics() {
        holdTimes.clear()
        pressPressLatencies.clear()
        releaseReleaseLatencies.clear()
        flightTimes.clear()
        afterPunctuationPauseTimes.clear()
        preCorrectionSlowingTimes.clear()
        postCorrectionSlowingTimes.clear()
        typingSpeedOverTime.clear()
        keyPressTimestamps.clear()

// Reset timing variables
        lastKeyPressTime = 0
        lastKeyReleaseTime = 0
        previousKeyReleaseTime = 0
        correctionDuration = 0
        lastInputLength = 0
        lastPunctuationTime = 0
        lastNonBackspaceKeyPressTime = 0L
        lastBackspaceReleaseTime = 0L
        hasWarnedExtraCharacters = false // Reset warning flag
        keyBounceCount = 0
        keyBounceFrequency = 0.0
        startTime = 0L

// `typingStartTime` is unused, removed from here.
        lastSpeedCaptureTime = 0L
        lastCapturedCharCount = 0
        currentPunctuationIndex = -1 // Not strictly used.

        realTimeTypingErrorCount = 0
    }



    /**
     * Resets the UI state and clears all collected metrics, preparing for a new typing attempt.
     */
    private fun resetPageState() {
        binding.textInput.text?.clear() // Clear text input using binding
        binding.progressBar.progress = 0 // Reset progress bar
        binding.submitButton.isEnabled = true // Enable submit button
        binding.textInput.isEnabled = true // Enable text input
        binding.refresh.isEnabled = false // Disable refresh until new typing starts
        isSubmitted = false // Reset submission status

        clearAllMetrics() // Call the consolidated function to clear all metrics

        // NEW: Reset character tracker state
        typedCharacters.forEach {
            it.isCorrect = null
            it.isCurrent = false
        }
        characterAdapter.notifyDataSetChanged() // Inform adapter that data has changed

        Toast.makeText(this, "Page reset. Start typing again!", Toast.LENGTH_SHORT).show()
    }


    /**
     * Displays an AlertDialog to confirm user logout.
     */
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit")
            .setMessage("Are you sure you want to log out of the application?")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() } // Dismiss dialog on Cancel
            .setPositiveButton("Yes") { _, _ -> userLoggingOut() } // Call logout function on Yes
            .show()
    }

    /**
     * Logs out the current user and navigates to the main (login) activity.
     */
    private fun userLoggingOut() {
// Clear shared preferences (if any user-specific data is stored there)
        getSharedPreferences("inputsp", Context.MODE_PRIVATE).edit().clear().apply()
        Firebase.auth.signOut() // Sign out from Firebase

// Navigate to LoginActivity, clearing the back stack to prevent returning here.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }



// --- Helper and Calculation Functions ---
    /**
     * Deletes the last character from the text input field.
     * This function is likely called from the custom keyboard.
     */
    fun deleteLastCharacter() {
        val editText = binding.textInput // Use binding to access text input
        val currentText = editText.text.toString()
        if (currentText.isNotEmpty()) {

// Remove the last character and update text
            editText.setText(currentText.substring(0, currentText.length - 1))
// Move cursor to the end of the new text.
            editText.text?.let { editText.setSelection(it.length) }
        }
    }

    /**
     * Appends a given character to the text input field.
     * This function is likely called from the custom keyboard.
     * @param char The character (as a String) to append.
     */
    fun appendTextToInput(char: String) {
        val inputField = binding.textInput // Use binding to access text input
        val current = inputField.text?.toString() ?: ""
        inputField.setText(current + char) // Append character
        inputField.setSelection((current + char).length) // Move cursor to the end
    }

    /**
     * Initializes device display metrics, specifically calculating the screen diagonal in inches.
     * This is used for device-specific normalization of keystroke features.
     */
    private fun initializeDeviceMetrics() {
        // DELETE 2 SPACES from this line
        val displayMetrics = DisplayMetrics() // This line should start with the same indentation as the others.
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val widthPixels = displayMetrics.widthPixels
        val heightPixels = displayMetrics.heightPixels
        val densityDpi = displayMetrics.densityDpi.toFloat()

        // Calculate diagonal using Pythagorean theorem and convert to inches.
        screenDiagonalInInches = sqrt((widthPixels * widthPixels + heightPixels * heightPixels).toDouble()) / densityDpi
    }

    /**
     * Displays the custom keyboard fragment.
     */
    private fun showCustomKeyboard() {
        val fragment = StandardizedKeyboard()
        supportFragmentManager.beginTransaction()
            .replace(R.id.keyboard_container, fragment) // Replace content of keyboard_container with the custom keyboard
            .setReorderingAllowed(true) // Allow reordering of fragment transactions
            .commit() // Commit the transaction

// Make the keyboard container visible
        findViewById<View>(R.id.keyboard_container).visibility = View.VISIBLE
    }

    /**
     * Disables copy-paste functionality for the text input field.
     * This prevents users from cheating by pasting the expected text.
     */
    private fun disableCopyPaste() {
        textInput.setCustomSelectionActionModeCallback(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false // Disable creation of action mode
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false // Disable preparation of action mode
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false // Disable click actions
            override fun onDestroyActionMode(mode: ActionMode?) {} // No action on destruction
        })
        textInput.isLongClickable = false // Prevent long clicks
        textInput.setTextIsSelectable(false) // Prevent text selection
    }



// `equalizeListSizes()` is intentionally commented out as per discussion.
// It would lead to data loss by truncating lists to the smallest size.
// For more complete data, it's better to handle varying list sizes in data analysis.
    /*
    private fun equalizeListSizes() {
    val minSize = listOf(
    holdTimes.size,
    pressPressLatencies.size,
    releaseReleaseLatencies.size,
    flightTimes.size
    ).minOrNull() ?: 0
    holdTimes.retainAll(holdTimes.take(minSize))
    pressPressLatencies.retainAll(pressPressLatencies.take(minSize))
    releaseReleaseLatencies.retainAll(releaseReleaseLatencies.take(minSize))
    flightTimes.retainAll(flightTimes.take(minSize))
    preCorrectionSlowingTimes.retainAll(preCorrectionSlowingTimes.take(minSize))
    postCorrectionSlowingTimes.retainAll(postCorrectionSlowingTimes.take(minSize))
    }
   */


    /**
     * Calculates the diagonal screen size in inches using display metrics.
     * @param context The application context.
     * @return The diagonal screen size in inches.
     */
   private fun getScreenSizeInInches(context: Context): Float {
        val metrics = context.resources.displayMetrics
        val widthInches = metrics.widthPixels / metrics.xdpi
        val heightInches = metrics.heightPixels / metrics.ydpi
        return sqrt(widthInches * widthInches + heightInches * heightInches)
    }


   /**
     * Calculates a density factor based on the device's DPI compared to a base DPI.
     * @param context The application context.
     * @return The density factor.
     */
    private fun getDensityFactor(context: Context): Double {
        val densityDpi = context.resources.displayMetrics.densityDpi
        val baseDpi = 320.0 // Reference device DPI (xhdpi)
        return densityDpi / baseDpi
    }


    /**
     * Calculates a physical adjustment factor for keystroke metrics, combining screen size and density.
     * This aims to make metrics more comparable across devices with different physical characteristics.
     * @param context The application context.
     * @return The combined physical adjustment factor.
     */
    private fun getPhysicalAdjustmentFactor(context: Context): Double {
        val screenSizeInches = getScreenSizeInInches(context)
        val baseScreenSize = 5.5 // Reference screen size in inches
        val screenSizeFactor = screenSizeInches / baseScreenSize
        val densityFactor = getDensityFactor(context)
        return (screenSizeFactor + densityFactor) / 2 // Average of screen size and density factors
    }

    /**
     * Adjusts a list of `Long` values (e.g., latencies) by dividing them by a given factor.
     * This is part of the device-independent normalization process.
     * @param values The list of `Long` values to adjust.
     * @param factor The adjustment factor (e.g., `physicalAdjustmentFactor`).
     * @return A new list with adjusted `Long` values.
     */
    private fun adjustForPhysicalDimensions(values: List<Double>, factor: Double): List<Double> {
// Edge case: If factor is zero, prevent division by zero. Return original list or handle as error.
// Assuming factor will always be positive from `getPhysicalAdjustmentFactor`.
        if (factor == 0.0) return values // Or throw an IllegalArgumentException
        return values.map { it / factor }
    }

    /**
     * Normalizes the input text by trimming whitespace, replacing multiple spaces with single spaces,
     * and converting to lowercase. This ensures consistent comparison with the expected text.
     * @param text The input text string.
     * @return The normalized text string.
     */
    private fun normalizeText(text: String): String {
        return text.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    }

    /**
     * Calculates the standard deviation of a list of `Long` values.
     * Used for variability analysis of metrics.
     * @param times The list of `Long` values (e.g., press-press latencies).
     * @return The standard deviation as a `Double`. Returns 0.0 if the list is empty.
     */
    private fun calculateStandardDeviation(times: List<Double>): Double {
        if (times.isEmpty()) return 0.0
        val mean = times.average()
        return sqrt(times.map { (it - mean).pow(2) }.average())
    }

    /**
     * Normalizes the character count by dividing the current input length by the expected text length.
     * @param inputLength The current length of the user's input.
     * @param expectedLength The length of the expected text.
     * @return A list containing a single normalized `Double` value (ratio of input to expected length).
     */
    private fun normalizeCharacterCount(inputLength: Int, expectedLength: Int): List<Double> {
// Edge case: Prevent division by zero if expectedLength is 0.
        return listOf(if (expectedLength > 0) inputLength.toDouble() / expectedLength else 0.0)
    }

    /**
     * Ensures that a `PostCorrectionSlowing` metric is recorded if the user submits
     * immediately after a backspace without typing any subsequent alphanumeric characters.
     * Prevents missing this specific metric in the data.
     */
    private fun ensurePostCsCompletion() {
        if (lastBackspaceReleaseTime != 0L) {
// If a backspace was released but no subsequent alphanumeric key was pressed
// before submission, add a 0L entry for Post-CS.
            postCorrectionSlowingTimes.add(0.0)
            lastBackspaceReleaseTime = 0L // Reset the flag
        }
    }

    /**
     * Saves all collected keystroke metrics to Firestore.
     * It structures the data into raw lists, adjusted lists, scalar values, and normalized values.
     * Handles both initial saving and updating existing user profiles.
     */
    private fun saveKeystrokeMetricsToFirestore() {
        val typingSpeedRawDoubles = typingSpeedOverTime.toList()
        val adjustmentFactor = getPhysicalAdjustmentFactor(this)

        Log.d("TypingMetrics", "Final TypingErrorRate Calculation Inputs: realTimeTypingErrorCount = $realTimeTypingErrorCount, expectedText.length = ${expectedText.length}")

        // Calculate the TypingErrorRate using the accumulated realTimeTypingErrorCount
        val typingErrorRate = if (expectedText.isNotEmpty()) {
            realTimeTypingErrorCount.toDouble() / expectedText.length.toDouble()
        } else {
            0.0 // If there's no expected text, the error rate is 0
        }

// Adjust list metrics for physical screen/density differences.
// These `adjusted` lists are *intermediate* steps.
        val adjustedHoldTimes = adjustForPhysicalDimensions(holdTimes, adjustmentFactor)
        val adjustedPressPressLatencies = adjustForPhysicalDimensions(pressPressLatencies, adjustmentFactor)
        val adjustedReleaseReleaseLatencies = adjustForPhysicalDimensions(releaseReleaseLatencies, adjustmentFactor)
        val adjustedFlightTimes = adjustForPhysicalDimensions(flightTimes, adjustmentFactor)
        val adjustedAfterPunctuationPauseTimes = adjustForPhysicalDimensions(afterPunctuationPauseTimes, adjustmentFactor)
        val adjustedPreCorrectionSlowingTimes = adjustForPhysicalDimensions(preCorrectionSlowingTimes, adjustmentFactor)
        val adjustedPostCorrectionSlowingTimes = adjustForPhysicalDimensions(postCorrectionSlowingTimes, adjustmentFactor)
        val adjustedTypingSpeedOverTime = adjustForPhysicalDimensions(typingSpeedRawDoubles, adjustmentFactor)

// Compile raw (unadjusted) list metrics.
        val rawListMetrics = mapOf(
            "HoldTime" to holdTimes.toList(),
            "PressPressLatency" to pressPressLatencies.toList(),
            "ReleaseReleaseLatency" to releaseReleaseLatencies.toList(),
            "FlightTime" to flightTimes.toList(),
            "AfterPunctuationPause" to afterPunctuationPauseTimes.toList(),
            "PreCorrectionSlowing" to preCorrectionSlowingTimes.toList(),
            "PostCorrectionSlowing" to postCorrectionSlowingTimes.toList(),
            "TypingSpeedOverTime" to typingSpeedRawDoubles
        )

// Compile scalar (single value) metrics.
// Calculate standard deviations for *adjusted* list metrics for scalar representation
       val interKeyIntervalStdDev = calculateStandardDeviation(adjustedPressPressLatencies) // Use adjusted
        val holdTimeStdDev = calculateStandardDeviation(adjustedHoldTimes) // Add this for variance
        val flightTimeStdDev = calculateStandardDeviation(adjustedFlightTimes) // Add this for variance
        val typingSpeedStdDev = calculateStandardDeviation(adjustedTypingSpeedOverTime) // Add this for variance

// Calculate averages of ADJUSTED list metrics for scalar representation
        val avgAdjustedHoldTime = adjustedHoldTimes.average()
        val avgAdjustedPressPressLatency = adjustedPressPressLatencies.average()
        val avgAdjustedReleaseReleaseLatency = adjustedReleaseReleaseLatencies.average()
        val avgAdjustedFlightTime = adjustedFlightTimes.average()
        val avgAdjustedAfterPunctuationPause = adjustedAfterPunctuationPauseTimes.average()
        val avgAdjustedPreCorrectionSlowing = adjustedPreCorrectionSlowingTimes.average()
        val avgAdjustedPostCorrectionSlowing = adjustedPostCorrectionSlowingTimes.average()
        val avgAdjustedTypingSpeed = adjustedTypingSpeedOverTime.average()


// ** --- NOW THE CRITICAL PART: FETCHING POPULATION BASELINES --- **
// For demonstration, these are PLACEHOLDER VALUES. YOU MUST REPLACE THESE
// WITH REAL MEANS AND STANDARD DEVIATIONS FROM YOUR HEALTHY CONTROL DATASET.
// You would fetch these from Firestore, e.g.,
// val globalBaselines = firestore.collection("global_baselines").document("keystroke_metrics").get().await()
// val populationMeanCorrectionDuration = globalBaselines.getDouble("avg_correction_duration_healthy") ?: 0.0
// val populationStdDevCorrectionDuration = globalBaselines.getDouble("stddev_correction_duration_healthy") ?: 1.0
// ... and so on for ALL the scalar metrics you want to normalize (means and std_devs)

// Example Placeholders (REPLACE THESE WITH REAL DATA!)
       val popMeans = mapOf(
           "CorrectionDuration" to 200.0,
            "KeyBounceFrequency" to 0.05,
            "InterKeyIntervalStdDev" to 15.0,
            "HoldTime" to 100.0,
            "PressPressLatency" to 150.0,
            "ReleaseReleaseLatency" to 140.0,
            "FlightTime" to 80.0,
            "AfterPunctuationPause" to 250.0,
            "PreCorrectionSlowing" to 300.0,
            "PostCorrectionSlowing" to 300.0,
            "TypingSpeed" to 2.0, // Avg CPS
            "HoldTimeStdDev" to 20.0,
            "FlightTimeStdDev" to 10.0,
            "TypingSpeedStdDev" to 0.5,
           "TypingErrorRate" to 0.05
       )


        val popStdDevs = mapOf(
            "CorrectionDuration" to 50.0,
            "KeyBounceFrequency" to 0.02,
            "InterKeyIntervalStdDev" to 5.0,
            "HoldTime" to 20.0,
            "PressPressLatency" to 30.0,
            "ReleaseReleaseLatency" to 25.0,
            "FlightTime" to 15.0,
            "AfterPunctuationPause" to 50.0,
            "PreCorrectionSlowing" to 60.0,
            "PostCorrectionSlowing" to 60.0,
            "TypingSpeed" to 0.5,
            "HoldTimeStdDev" to 5.0,
            "FlightTimeStdDev" to 3.0,
            "TypingSpeedStdDev" to 0.2,
            "TypingErrorRate" to 0.03
        )

// Define a general Z-score normalization function
        fun calculateZScore(value: Double, mean: Double, stdDev: Double): Double {
            if (stdDev == 0.0) return 0.0 // Or handle as NaN / very large number if value != mean
            return (value - mean) / stdDev
        }

// Now, create the map of truly normalized scalar metrics (average of adjusted values)
        val trulyNormalizedScalarMetrics = mapOf(
            "NormalizedCorrectionDuration" to calculateZScore(
                correctionDuration.toDouble(),
                popMeans["CorrectionDuration"] ?: 0.0,
                popStdDevs["CorrectionDuration"] ?: 1.0
            ),
            "NormalizedKeyBounceFrequency" to calculateZScore(
                keyBounceFrequency,
                popMeans["KeyBounceFrequency"] ?: 0.0,
                popStdDevs["KeyBounceFrequency"] ?: 1.0
            ),
            "NormalizedInterKeyIntervalStdDev" to calculateZScore(
                interKeyIntervalStdDev,
                popMeans["InterKeyIntervalStdDev"] ?: 0.0,
                popStdDevs["InterKeyIntervalStdDev"] ?: 1.0
            ),
            "NormalizedAvgHoldTime" to calculateZScore(
                avgAdjustedHoldTime,
                popMeans["HoldTime"] ?: 0.0,
                popStdDevs["HoldTime"] ?: 1.0
            ),
            "NormalizedAvgPressPressLatency" to calculateZScore(
                avgAdjustedPressPressLatency,
                popMeans["PressPressLatency"] ?: 0.0,
                popStdDevs["PressPressLatency"] ?: 1.0
            ),
            "NormalizedAvgReleaseReleaseLatency" to calculateZScore(
                avgAdjustedReleaseReleaseLatency,
                popMeans["ReleaseReleaseLatency"] ?: 0.0,
                popStdDevs["ReleaseReleaseLatency"] ?: 1.0
            ),
            "NormalizedAvgFlightTime" to calculateZScore(
                avgAdjustedFlightTime,
                popMeans["FlightTime"] ?: 0.0,
                popStdDevs["FlightTime"] ?: 1.0
            ),
            "NormalizedAvgAfterPunctuationPause" to calculateZScore(
                avgAdjustedAfterPunctuationPause,
                popMeans["AfterPunctuationPause"] ?: 0.0,
                popStdDevs["AfterPunctuationPause"] ?: 1.0
            ),
            "NormalizedAvgPreCorrectionSlowing" to calculateZScore(
                avgAdjustedPreCorrectionSlowing,
                popMeans["PreCorrectionSlowing"] ?: 0.0,
                popStdDevs["PreCorrectionSlowing"] ?: 1.0
            ),
            "NormalizedAvgPostCorrectionSlowing" to calculateZScore(
                avgAdjustedPostCorrectionSlowing,
                popMeans["PostCorrectionSlowing"] ?: 0.0,
                popStdDevs["PostCorrectionSlowing"] ?: 1.0
            ),
            "NormalizedAvgTypingSpeed" to calculateZScore(
                avgAdjustedTypingSpeed,
                popMeans["TypingSpeed"] ?: 0.0,
                popStdDevs["TypingSpeed"] ?: 1.0
            ),
            "NormalizedHoldTimeStdDev" to calculateZScore(
                holdTimeStdDev,
                popMeans["HoldTimeStdDev"] ?: 0.0,
                popStdDevs["HoldTimeStdDev"] ?: 1.0
            ),
            "NormalizedFlightTimeStdDev" to calculateZScore(
                flightTimeStdDev,
                popMeans["FlightTimeStdDev"] ?: 0.0,
                popStdDevs["FlightTimeStdDev"] ?: 1.0
            ),
            "NormalizedTypingSpeedStdDev" to calculateZScore(
                typingSpeedStdDev,
                popMeans["TypingSpeedStdDev"] ?: 0.0,
                popStdDevs["TypingSpeedStdDev"] ?: 1.0
            ),
            "NormalizedTypingErrorRate" to calculateZScore( // <--- ADD THIS LINE
                typingErrorRate,
                popMeans["TypingErrorRate"] ?: 0.0,
                popStdDevs["TypingErrorRate"] ?: 1.0
            )

        )


// Combine all metrics into a single map for Firestore.
        val currentMetrics = mapOf(
            "RawMetrics" to rawListMetrics, // Original raw lists
            "AdjustedScalarMetrics" to mapOf( // Group these for clarity, they are now the "features"
                "AvgAdjustedHoldTime" to avgAdjustedHoldTime,
                "AvgAdjustedPressPressLatency" to avgAdjustedPressPressLatency,
                "AvgAdjustedReleaseReleaseLatency" to avgAdjustedReleaseReleaseLatency,
                "AvgAdjustedFlightTime" to avgAdjustedFlightTime,
                "AvgAdjustedAfterPunctuationPause" to avgAdjustedAfterPunctuationPause,
                "AvgAdjustedPreCorrectionSlowing" to avgAdjustedPreCorrectionSlowing,
                "AvgAdjustedPostCorrectionSlowing" to avgAdjustedPostCorrectionSlowing,
                "AvgAdjustedTypingSpeed" to avgAdjustedTypingSpeed,
                "CorrectionDuration" to correctionDuration.toDouble(),
                "KeyBounceFrequency" to keyBounceFrequency,
                "InterKeyIntervalStdDev" to interKeyIntervalStdDev,
                "HoldTimeStdDev" to holdTimeStdDev,
                "FlightTimeStdDev" to flightTimeStdDev,
                "TypingSpeedStdDev" to typingSpeedStdDev,
                "TypingErrorRate" to typingErrorRate, // New scalar metric

                // NormalizedCharacterCount: This is already a ratio, you might not want to Z-score it further.
                // It's already normalized against expected length. Keep it as is or decide if it also needs population normalization.
                "NormalizedCharacterCount" to normalizeCharacterCount(textInput.text?.length ?: 0, expectedText.length).first()
            ),
            "NormalizedFeatures" to trulyNormalizedScalarMetrics, // These are your Z-scored features for ML
            "Timestamp" to getCurrentTimestamp()
        )

// Get a reference to the user's document in Firestore.
        val userDocRef = firestore.collection("User_profile").document(userId)

// Check if the user document exists.
        userDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
// If document exists, update it by adding the new metrics entry to the "metrics" array.
                    val profileUpdate = hashMapOf(
                        "metrics" to FieldValue.arrayUnion(currentMetrics) // Use arrayUnion to append to the array
                    )
                    userDocRef.set(profileUpdate, SetOptions.merge()) // Merge to update only the "metrics" field
                        .addOnSuccessListener {
                            Toast.makeText(this, "Metrics updated successfully.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
// If document does not exist, create a new document with the metrics array.
                    val profile = hashMapOf("metrics" to arrayListOf(currentMetrics))
                    userDocRef.set(profile)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Metrics saved successfully.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to save metrics: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
// Handle failure to retrieve user document.
                Toast.makeText(this, "Failed to retrieve user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Generates a formatted string of the current timestamp.
     * @return Current timestamp as "yyyy-MM-dd HH:mm:ss".
     */
    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

// This extension function is defined but not used in the provided code.
// It can be removed if there's no intent to use it.
    /*
    private fun List<Long>.averageOrNull(): Double? {
    return if (this.isNotEmpty()) this.average() else 0.0
    }
    */
// The `ensurePostCsOnSubmit()` function was declared but not called, and its logic
// was effectively merged into `ensurePostCsCompletion()`. It has been removed.
}