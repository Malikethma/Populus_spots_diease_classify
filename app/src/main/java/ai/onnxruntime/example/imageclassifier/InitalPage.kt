package ai.onnxruntime.example.imageclassifier

import ai.onnxruntime.example.imageclassifier.databinding.ActivityInitalPageBinding
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.log

class InitalPage : AppCompatActivity() {
    private lateinit var binding: ActivityInitalPageBinding
    public val TAG = "InitalPage"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityInitalPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button1.setOnClickListener {
            Log.d(TAG, "Button1 clicked")
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        binding.button2.setOnClickListener {
            Log.d(TAG, "Button2 clicked")
            val intent = Intent(this, SelectImage::class.java)
            Log.d(TAG, "Intent created: $intent")
            startActivity(intent)
            Log.d(TAG, "startActivity called with intent: $intent")
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}