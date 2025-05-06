package com.example.newsfetch

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.CalendarView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_main)

        // Initialize views
        newsLayout = findViewById(R.id.newsLayout)
        calendarView = findViewById(R.id.calendarView)


        // Set max date to today (prevent future date selection)
        calendarView.maxDate = System.currentTimeMillis()


        // Set up calendar listener
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }
            val selectedDate = dateFormat.format(calendar.time)
            fetchNews(selectedDate)
        }

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    db = FirebaseFirestore.getInstance()
                    // Fetch news for today's date by default
                    val today = dateFormat.format(Calendar.getInstance().time)
                    fetchNews(today)
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
                val matchingDocs = result.documents.filter { doc ->
                    val timestamp = doc.getTimestamp("date")?.toDate()
                    val dateStr = timestamp?.let { dateFormat.format(it) }
                    dateStr == targetDay
                }

                newsLayout.removeAllViews() // Clear previous results

                if (matchingDocs.isNotEmpty()) {
                    for (doc in matchingDocs) {
                        renderDocument(doc)
                    }
                } else {
                    val errorCard = createErrorCard("No articles found for $targetDay")
                    newsLayout.addView(errorCard)
                }
            }
            .addOnFailureListener { e ->
                val errorCard = createErrorCard("Error fetching: ${e.message}")
                newsLayout.addView(errorCard)
            }
    }



    private fun renderDocument(document: DocumentSnapshot) {
        val title = document.getString("title") ?: "N/A"
        val link = document.getString("link") ?: "N/A"

        val cardView = CardView(this)
        val cardLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        // Add margin between cards
        cardLayoutParams.setMargins(0, 0, 0, 16)
        cardView.layoutParams = cardLayoutParams
        cardView.setCardBackgroundColor(Color.DKGRAY)
        cardView.radius = 35f
        cardView.cardElevation = 8f

        // Using RelativeLayout to position the "Read More" text at the bottom right
        val cardLayout = android.widget.RelativeLayout(this)
        cardLayout.setPadding(16, 16, 16, 16)
        // Ensure minimum height for the card content
        val minHeightInPixels = (120 * resources.displayMetrics.density).toInt()
        cardLayout.minimumHeight = minHeightInPixels

        // Give unique IDs to our views for RelativeLayout positioning
        val titleId = android.view.View.generateViewId()
        val readMoreId = android.view.View.generateViewId()

        // Title without the "Title:" prefix
        val titleView = TextView(this)
        titleView.id = titleId
        titleView.text = title
        titleView.textSize = 20f
        titleView.setTextColor(Color.WHITE)

        // Set up the RelativeLayout parameters for the title
        val titleParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        titleParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
        // Add right margin to prevent overlap with "Read More"
        titleParams.rightMargin = (80 * resources.displayMetrics.density).toInt()
        titleView.layoutParams = titleParams

        // Create a small text-only "Read More" link
        val readMoreText = TextView(this)
        readMoreText.id = readMoreId
        readMoreText.text = "Read More"
        readMoreText.textSize = 14f
        readMoreText.setTextColor(Color.WHITE)

        // Set up the RelativeLayout parameters for the "Read More" text
        val readMoreParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        readMoreParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT)
        readMoreParams.addRule(android.widget.RelativeLayout.BELOW, titleId)
        readMoreParams.topMargin = 16 // Add some margin between title and read more
        readMoreText.layoutParams = readMoreParams

        // Set up click listener to open the URL in a browser
        readMoreText.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse(link)
            startActivity(intent)
        }

        // Add views to the RelativeLayout
        cardLayout.addView(titleView)
        cardLayout.addView(readMoreText)

        cardView.addView(cardLayout)

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
        cardView.setCardBackgroundColor(Color.rgb(255, 240, 240))  // Light red background
        cardView.radius = 16f
        cardView.cardElevation = 8f

        val textLayout = LinearLayout(this)
        textLayout.orientation = LinearLayout.VERTICAL
        textLayout.setPadding(16, 16, 16, 16)

        val errorView = TextView(this)
        errorView.text = errorMessage
        errorView.textSize = 16f
        errorView.setTextColor(Color.RED)

        textLayout.addView(errorView)
        cardView.addView(textLayout)

        return cardView
    }
}