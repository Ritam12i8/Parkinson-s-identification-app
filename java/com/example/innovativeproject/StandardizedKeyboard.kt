package com.example.innovativeproject

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class StandardizedKeyboard : Fragment() {

    private lateinit var toggleButton: Button
    private lateinit var spaceButton: Button
    private lateinit var submitButton: Button
    private lateinit var shiftButton: Button
    private lateinit var alphabetLayout: LinearLayout
    private lateinit var numberLayout: LinearLayout

    private var isShiftOn = true  // Shift ON by default
    private lateinit var rootView: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.activity_standardized_keyboard, container, false)

        // Handle window insets (safe area padding)
        ViewCompat.setOnApplyWindowInsetsListener(rootView.findViewById(R.id.keyboardLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        alphabetLayout = rootView.findViewById(R.id.alphabetLayout)
        numberLayout = rootView.findViewById(R.id.numberLayout)
        toggleButton = rootView.findViewById(R.id.keyToggle123)
        spaceButton = rootView.findViewById(R.id.keySpace)
        submitButton = rootView.findViewById(R.id.keySubmit)
        shiftButton = rootView.findViewById(R.id.keyShift)

        // Initialize shift ON appearance and uppercase keys at start
        shiftButton.alpha = 1.0f
        updateAlphabetKeysCase()

        toggleButton.setOnClickListener {
            if (alphabetLayout.visibility == View.VISIBLE) {
                alphabetLayout.visibility = View.GONE
                numberLayout.visibility = View.VISIBLE
                toggleButton.text = "ABC"
            } else {
                alphabetLayout.visibility = View.VISIBLE
                numberLayout.visibility = View.GONE
                toggleButton.text = "123"
            }
        }

        spaceButton.setOnClickListener {
            val activity = activity as? AfterSignUp
            activity?.appendTextToInput(" ")  // Append a space character
          //  showToast("SPACE")
        }

        submitButton.setOnClickListener {
            showToast("Keyboard closed. Please press submit.")
            requireActivity().supportFragmentManager.beginTransaction()
                .remove(this@StandardizedKeyboard)
                .commit()
            requireActivity().findViewById<View>(R.id.keyboard_container).visibility = View.GONE
        }

        // Shift key toggle functionality
        shiftButton.setOnClickListener {
            isShiftOn = !isShiftOn
            updateAlphabetKeysCase()
            shiftButton.alpha = if (isShiftOn) 1.0f else 0.5f
        }

        val allKeyIds = listOf(
            // Alphabets
            R.id.keyQ, R.id.keyW, R.id.keyE, R.id.keyR, R.id.keyT, R.id.keyY, R.id.keyU,
            R.id.keyI, R.id.keyO, R.id.keyP, R.id.keyA, R.id.keyS, R.id.keyD, R.id.keyF,
            R.id.keyG, R.id.keyH, R.id.keyJ, R.id.keyK, R.id.keyL, R.id.keyZ, R.id.keyX,
            R.id.keyC, R.id.keyV, R.id.keyB, R.id.keyN, R.id.keyM,

            // Numbers
            R.id.key1, R.id.key2, R.id.key3, R.id.key4, R.id.key5,
            R.id.key6, R.id.key7, R.id.key8, R.id.key9, R.id.key0,

            // Special Characters
            R.id.keyExclaim, R.id.keyAt, R.id.keyHash, R.id.keyDollar,
            R.id.keyPercent, R.id.keyAmp, R.id.keyStar,
            R.id.keyComma, R.id.keyDot, R.id.keyUnderScore,
            R.id.keyOpenBracket, R.id.keyCloseBracket,
            R.id.keyPlus, R.id.keyMinus, R.id.keySlash,
            R.id.keyColon, R.id.keySemicolon, R.id.keyQuotes, R.id.keyDoubleQuotes,
            R.id.keyQuestion
        )

        rootView.findViewById<Button>(R.id.keyBackspace2)?.setOnClickListener {
            val activity = activity as? AfterSignUp
            activity?.deleteLastCharacter()
         //   showToast("Backspace")
        }

        rootView.findViewById<Button>(R.id.keyBackspace1)?.setOnClickListener {
            val activity = activity as? AfterSignUp
            activity?.deleteLastCharacter()
           // showToast("Backspace")
        }

        for (id in allKeyIds) {
            rootView.findViewById<Button>(id)?.setOnClickListener { btn ->
                val key = (btn as Button).text.toString()
             //   showToast(key)

                val finalKey = if (isShiftOn && id in alphabetKeyIds()) {
                    key.uppercase()
                } else {
                    key.lowercase()
                }

                val activity = activity as? AfterSignUp
                activity?.appendTextToInput(finalKey)
            }
        }

        return rootView
    }

    private fun alphabetKeyIds() = listOf(
        R.id.keyQ, R.id.keyW, R.id.keyE, R.id.keyR, R.id.keyT, R.id.keyY, R.id.keyU,
        R.id.keyI, R.id.keyO, R.id.keyP, R.id.keyA, R.id.keyS, R.id.keyD, R.id.keyF,
        R.id.keyG, R.id.keyH, R.id.keyJ, R.id.keyK, R.id.keyL, R.id.keyZ, R.id.keyX,
        R.id.keyC, R.id.keyV, R.id.keyB, R.id.keyN, R.id.keyM
    )

    private fun updateAlphabetKeysCase() {
        for (id in alphabetKeyIds()) {
            val keyButton = rootView.findViewById<Button>(id)
            keyButton?.text = if (isShiftOn) {
                keyButton.text.toString().uppercase()
            } else {
                keyButton.text.toString().lowercase()
            }
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(requireContext(), "Key Pressed: $text", Toast.LENGTH_SHORT).show()
    }
}