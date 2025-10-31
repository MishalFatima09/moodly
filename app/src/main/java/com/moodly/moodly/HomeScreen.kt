package com.moodly.moodly
//
//import android.os.Bundle
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import androidx.recyclerview.widget.RecyclerView
//import androidx.recyclerview.widget.StaggeredGridLayoutManager
//
//class HomeScreen : AppCompatActivity() {
//
//    lateinit var recyclerView: RecyclerView
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_home_screen)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        recyclerView = findViewById(R.id.recycler_view_posts)
//        val layoutManager = StaggeredGridLayoutManager(
//            2,
//            StaggeredGridLayoutManager.VERTICAL)
//
//        recyclerView.layoutManager = layoutManager
//    }
//}


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

data class Post(
    val id: Int,
    val title: String,
    val heightRatio: Float
)

class PostAdapter(private val data: List<Post>) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private fun getColor(position: Int): String {
        return when (position % 5) {
            0 -> "#FF6B6B" // Light Red
            1 -> "#4ECDC4" // Teal
            2 -> "#45B7D1" // Blue
            3 -> "#F7F671" // Yellow
            4 -> "#A178D1" // Purple
            else -> "#CCCCCC"
        }
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val postImage: ImageView = itemView.findViewById(R.id.post_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        // Inflate the item_post.xml layout
        val view = LayoutInflater.from(parent.context).inflate(R.layout.pin_item, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val item = data[position]

        val density = holder.itemView.context.resources.displayMetrics.density
        val baseHeightDp = 200
        val baseHeightPx = (baseHeightDp * density).toInt()

        val actualHeight = (baseHeightPx * item.heightRatio).toInt()

        holder.postImage.layoutParams.height = actualHeight

        holder.postImage.setBackgroundColor(Color.parseColor(getColor(position)))

        val params = holder.itemView.layoutParams as StaggeredGridLayoutManager.LayoutParams
        params.isFullSpan = false // Ensure it stays in a column and doesn't span across both
        holder.itemView.layoutParams = params
    }

    override fun getItemCount(): Int = data.size
}


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
    private fun getSampleData(): List<Post> {
        val ratios = listOf(1.5f, 0.8f, 1.2f, 1.8f, 0.9f, 1.1f, 1.6f, 0.7f, 1.4f, 1.0f, 1.7f, 0.6f)
        return (1..20).map { id ->
            Post(
                id = id,
                title = "Post $id",
                heightRatio = ratios[id % ratios.size]
            )
        }
    }
}
