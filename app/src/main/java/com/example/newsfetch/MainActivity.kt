package com.example.newsfetch

import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CalendarView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var newsLayout: LinearLayout
    private lateinit var calendarView: CalendarView
    private lateinit var dateDisplay: TextView
    private lateinit var calendarToggleButton: ImageButton
    private lateinit var prevDateButton: ImageButton
    private lateinit var nextDateButton: ImageButton
    private lateinit var categorySpinner: Spinner
    private lateinit var savedNewsButton: ImageButton
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var currentDate = Calendar.getInstance()
    private var currentCategory = "All"
    private var allNewsForDate = listOf<DocumentSnapshot>()
    private val savedNewsIds = mutableSetOf<String>()
    private var isShowingSavedNews = false
    private var allNews = listOf<DocumentSnapshot>()

    private val categories = listOf(
        "All",
        "Business",
        "Politics",
        "World Affairs",
        "Science"
    )

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_main)

        // Initialize views
        newsLayout = findViewById(R.id.newsLayout)
        calendarView = findViewById(R.id.calendarView)
        dateDisplay = findViewById(R.id.dateDisplay)
        prevDateButton = findViewById(R.id.prevDateButton)
        nextDateButton = findViewById(R.id.nextDateButton)
        categorySpinner = findViewById(R.id.categorySpinner)
        savedNewsButton = findViewById(R.id.savedNewsButton)

        dateDisplay.paintFlags = dateDisplay.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // Load saved news IDs from SharedPreferences
        loadSavedNewsIds()

        // Set up saved news button
        savedNewsButton.setOnClickListener {
            isShowingSavedNews = !isShowingSavedNews
            if (isShowingSavedNews) {
                displaySavedNews()
                savedNewsButton.setImageResource(R.drawable.saved_bookmarks_active)
            } else {
                filterAndDisplayNews()
                savedNewsButton.setImageResource(R.drawable.saved_bookmarks)
            }
        }

        // Set up category spinner
        val adapter = ArrayAdapter(this, R.layout.spinner_item, categories, )
        adapter.setDropDownViewResource(R.layout.spinner_item2)
        categorySpinner.adapter = adapter
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentCategory = categories[position]
                filterAndDisplayNews()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                currentCategory = "All"
                filterAndDisplayNews()
            }
        }

        // Set max date to today (prevent future date selection)
        calendarView.maxDate = System.currentTimeMillis()

        // Initially hide the calendar
        calendarView.visibility = View.GONE

        // Set up calendar toggle button
        dateDisplay.setOnClickListener {
            if (calendarView.visibility == View.VISIBLE) {
                calendarView.visibility = View.GONE
            } else {
                calendarView.visibility = View.VISIBLE
                // Set the calendar to the current selected date
                calendarView.date = currentDate.timeInMillis
            }
        }

        // Set up date navigation buttons
        prevDateButton.setOnClickListener {
            val prevDay = (currentDate.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, -1)
            }

            // Update currentDate with the new value
            currentDate = prevDay
            updateDateDisplay()
            fetchNews(dateFormat.format(currentDate.time))
            // Update navigation buttons state after changing date
            updateNavigationButtonsState()
        }

        nextDateButton.setOnClickListener {
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val nextDay = (currentDate.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 1)
            }

            // Create normalized comparison calendars for accurate date comparison
            val normalizedNextDay = Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, nextDay.get(Calendar.YEAR))
                set(Calendar.MONTH, nextDay.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, nextDay.get(Calendar.DAY_OF_MONTH))
            }

            val normalizedToday = Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, today.get(Calendar.YEAR))
                set(Calendar.MONTH, today.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
            }

            // Log the date comparison for debugging
            Log.d("DateNavigation", "Next day: ${dateFormat.format(nextDay.time)}")
            Log.d("DateNavigation", "Today: ${dateFormat.format(today.time)}")
            Log.d("DateNavigation", "Comparison: ${normalizedNextDay.timeInMillis <= normalizedToday.timeInMillis}")

            if (normalizedNextDay.timeInMillis <= normalizedToday.timeInMillis) {
                // Update currentDate with the new value
                currentDate = nextDay
                updateDateDisplay()
                fetchNews(dateFormat.format(currentDate.time))
                // Update navigation buttons state after changing date
                updateNavigationButtonsState()
            } else {
                // Show a toast message to inform the user they can't navigate beyond today
                Toast.makeText(
                    this,
                    "Cannot navigate to future dates",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Set up calendar listener
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            currentDate.set(year, month, dayOfMonth)
            updateDateDisplay()
            fetchNews(dateFormat.format(currentDate.time))
            calendarView.visibility = View.GONE
            // Update navigation buttons state after date selection
            updateNavigationButtonsState()
        }

        // Initialize the navigation button states
        updateNavigationButtonsState()

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    db = FirebaseFirestore.getInstance()
                    // Fetch all news first
                    fetchAllNews()
                    // Then fetch news for today's date by default
                    updateDateDisplay()
                    fetchNews(dateFormat.format(currentDate.time))
                } else {
                    val errorCard = createErrorCard("Auth failed: ${authTask.exception?.message}")
                    newsLayout.addView(errorCard)
                }
            }
    }

    private fun fetchNews(targetDay: String) {
        db.collection("headlines")
            .get()
            .addOnSuccessListener { result ->
                allNewsForDate = result.documents.filter { doc ->
                    val timestamp = doc.getTimestamp("date")?.toDate()
                    val dateStr = timestamp?.let { dateFormat.format(it) }
                    dateStr == targetDay
                }
                filterAndDisplayNews()
            }
            .addOnFailureListener { e ->
                val errorCard = createErrorCard("Error fetching: ${e.message}")
                newsLayout.addView(errorCard)
            }
    }

    private fun fetchAllNews() {
        db.collection("headlines")
            .get()
            .addOnSuccessListener { result ->
                allNews = result.documents
            }
            .addOnFailureListener { e ->
                Log.e("NewsFetch", "Error fetching all news: ${e.message}")
            }
    }

    private fun updateNavigationButtonsState() {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        // Create normalized comparison calendars (strip time component)
        val normalizedToday = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, today.get(Calendar.YEAR))
            set(Calendar.MONTH, today.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
        }

        val normalizedCurrent = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, currentDate.get(Calendar.YEAR))
            set(Calendar.MONTH, currentDate.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, currentDate.get(Calendar.DAY_OF_MONTH))
        }

        // Log the date comparison for debugging
        val isToday = normalizedCurrent.timeInMillis == normalizedToday.timeInMillis
        val canGoForward = normalizedCurrent.timeInMillis < normalizedToday.timeInMillis

        Log.d("DateNavigation", "Current date: ${dateFormat.format(currentDate.time)}")
        Log.d("DateNavigation", "Today: ${dateFormat.format(today.time)}")
        Log.d("DateNavigation", "Is today: $isToday")
        Log.d("DateNavigation", "Can go forward: $canGoForward")

        // Disable next button only if we're at today's date
        nextDateButton.isEnabled = !isToday && canGoForward
        nextDateButton.isVisible = !isToday && canGoForward

        // Previous button is always enabled - we can always go back in time
        prevDateButton.isEnabled = true
    }

    private fun filterAndDisplayNews() {
        newsLayout.removeAllViews()
        if (isShowingSavedNews) {
            displaySavedNews()
            return
        }

        val filteredNews = if (currentCategory == "All") {
            allNewsForDate
        } else {
            allNewsForDate.filter { doc ->
                doc.getString("category") == currentCategory
            }
        }

        if (filteredNews.isEmpty()) {
            val noNewsCard = createErrorCard("No news found for selected date and category")
            newsLayout.addView(noNewsCard)
        } else {
            filteredNews.forEach { doc -> renderDocument(doc) }
        }
    }

    private fun updateDateDisplay() {
        val today = Calendar.getInstance()
        val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        dateDisplay.text = when {
            currentDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    currentDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
            currentDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    currentDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
            currentDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    currentDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) + 1 -> "Tomorrow"
            else -> displayFormat.format(currentDate.time)
        }
    }

    private fun renderDocument(document: DocumentSnapshot) {
        val title = document.getString("title") ?: "N/A"
        val link = document.getString("link") ?: "N/A"
        val description = document.getString("description") ?: "N/A"

        val timestamp = document.getTimestamp("date")?.toDate()
        val date = timestamp?.let { dateFormat.format(it) }
        val category = document.getString("category") ?: "N/A"

        // Inflate the custom card layout
        val cardView = layoutInflater.inflate(R.layout.news_card, newsLayout, false) as CardView
        cardView.setBackgroundColor(Color.rgb(0,0,0))

        // Get references to the TextViews
        val titleView = cardView.findViewById<TextView>(R.id.newsTitle)
        val categoryView = cardView.findViewById<TextView>(R.id.newsCategory)
        val descriptionView = cardView.findViewById<TextView>(R.id.newsDescription)
        val dateView =  cardView.findViewById<TextView>(R.id.newsDate)
        val saveButton = cardView.findViewById<ImageButton>(R.id.saveButton)

        // Set the text
        titleView.text = title
        descriptionView.text = description
        categoryView.text = category
        dateView.text = date

        // Set up save button


        saveButton.setImageResource(if (savedNewsIds.contains(document.id)) (R.drawable.bookmark_active) else (R.drawable.bookmark))
        saveButton.setOnClickListener {
            if (savedNewsIds.contains(document.id)) {
                removeNewsId(document.id)
                saveButton.setImageResource(R.drawable.bookmark)
            } else {
                saveNewsId(document.id)
                saveButton.setImageResource(R.drawable.bookmark_active)
            }
        }

        // Set up click listener to open the URL in a browser
        cardView.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse(link)
            startActivity(intent)
        }

        // Add the card to the newsLayout
        newsLayout.addView(cardView)
    }

    private fun createErrorCard(errorMessage: String): CardView {
        val cardView = CardView(this)
        val cardLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardView.layoutParams = cardLayoutParams
        cardView.setBackgroundColor(Color.rgb(0,0,0))  // Light red background
        cardView.radius = 16f
        cardView.cardElevation = 8f

        val textLayout = LinearLayout(this)
        textLayout.orientation = LinearLayout.VERTICAL
        textLayout.setPadding(16, 16, 16, 16)

        val errorView = TextView(this)
        errorView.text = errorMessage
        errorView.textSize = 16f
        errorView.setTextColor(Color.WHITE)

        textLayout.addView(errorView)
        cardView.addView(textLayout)

        return cardView
    }

    private fun loadSavedNewsIds() {
        val sharedPrefs = getSharedPreferences("NewsPrefs", MODE_PRIVATE)
        savedNewsIds.clear()
        savedNewsIds.addAll(sharedPrefs.getStringSet("savedNewsIds", setOf()) ?: setOf())
    }

    private fun saveNewsId(newsId: String) {
        savedNewsIds.add(newsId)
        val sharedPrefs = getSharedPreferences("NewsPrefs", MODE_PRIVATE)
        sharedPrefs.edit().putStringSet("savedNewsIds", savedNewsIds).apply()
    }

    private fun removeNewsId(newsId: String) {
        savedNewsIds.remove(newsId)
        val sharedPrefs = getSharedPreferences("NewsPrefs", MODE_PRIVATE)
        sharedPrefs.edit().putStringSet("savedNewsIds", savedNewsIds).apply()
    }

    private fun displaySavedNews() {
        newsLayout.removeAllViews()
        val savedNews = allNews.filter { doc -> savedNewsIds.contains(doc.id) }
        if (savedNews.isEmpty()) {
            val noSavedNewsCard = createErrorCard("No saved news found")
            newsLayout.addView(noSavedNewsCard)
        } else {
            // Sort saved news by date in descending order (newest first)
            val sortedSavedNews = savedNews.sortedByDescending { doc ->
                doc.getTimestamp("date")?.toDate()?.time ?: 0L
            }
            sortedSavedNews.forEach { doc -> renderDocument(doc) }
        }
    }
}