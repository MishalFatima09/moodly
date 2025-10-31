package com.moodly.moodly

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.moodly.moodly.R
class HomeScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_screen)

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view_posts)

        val layoutManager = StaggeredGridLayoutManager(
            2, // spanCount: Number of columns
            StaggeredGridLayoutManager.VERTICAL // orientation
        )

        recyclerView.layoutManager = layoutManager

        val sampleData = getSampleData()
        recyclerView.adapter = PostAdapter(sampleData)
    }

    /**
     * Generates mock data with different height ratios (simulating tall and short images).
     * Ratios less than 1.0f will be shorter than the base, and greater than 1.0f will be taller.
     */
    private fun getSampleData(): List<DATA_Post> {
        val ratios = listOf(1.5f, 0.8f, 1.2f, 1.8f, 0.9f, 1.1f, 1.6f, 0.7f, 1.4f, 1.0f, 1.7f, 0.6f)
        return (1..20).map { id ->
            DATA_Post(
                id = id,
                title = "Post $id",
                heightRatio = ratios[id % ratios.size]
            )
        }
    }
}
