package ai.onnxruntime.example.imageclassifier

import ai.onnxruntime.*
import ai.onnxruntime.example.imageclassifier.databinding.ActivitySelectImageBinding
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SelectImage : AppCompatActivity() {
    private lateinit var binding: ActivitySelectImageBinding
    public val TAG = "SelectImage"
    private val PICK_IMAGE_REQUEST = 1

    private lateinit var ortEnv: OrtEnvironment
    private lateinit var ortSession: OrtSession
    private lateinit var ortAnalyzer: ORTAnalyzer

    private val backgroundExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private val labelData: List<String> by lazy { readLabels() }
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "SelectImage Activity created")
        binding = ActivitySelectImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.selectImage.setOnClickListener({
            Log.d(TAG, "SelectImage clicked")
            openGallery()
        })

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ortEnv = OrtEnvironment.getEnvironment()
        scope.launch {
            try {
                ortSession = createOrtSession()
                ortAnalyzer = ORTAnalyzer(ortSession, ::updateUI)
            } catch (e: Exception) {
                Log.e(TAG, "ORT session creation failed", e)
                // 添加UI错误提示
            }
    }

    private fun openGallery() {
        Log.d(TAG, "openGallery clicked")
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            val selectedImageUri: Uri? = data?.data
            selectedImageUri?.let {
                val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                // 缩放Bitmap到256x256
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
                binding.imageView.setImageBitmap(resizedBitmap)
                recognizeImage(resizedBitmap)
            }
        }
    }

    private suspend fun readModels(): ByteArray = withContext(Dispatchers.IO) {
        resources.openRawResource(R.raw.resnet50_populus).readBytes()
    }
    private suspend fun createOrtSession(): OrtSession = withContext(Dispatchers.Default) {
        ortEnv.createSession(readModels()) ?: throw IllegalStateException("Failed to create ORT session")
    }

    private fun readLabels(): List<String> {
        return resources.openRawResource(R.raw.classify).bufferedReader().readLines()
    }

    private fun recognizeImage(bitmap: Bitmap) {
        val tensor = convertBitmapToTensor(bitmap)
        ortAnalyzer.analyze(tensor)
    }

    private fun updateUI(result: Result) {
        runOnUiThread {
            // 获取最高概率值（第一个结果）
            val maxScore = result.detectedScore.firstOrNull() ?: 0f

            // 更新进度条（0-100范围）
            binding.percentMeter.progress = (maxScore * 100).toInt()

            // 显示类别和概率
            val labelIndex = result.detectedIndices.firstOrNull() ?: 0
            binding.analyzeItem.text = labelData.getOrNull(labelIndex) ?: "未知类别"
            binding.analyzeResult.text = "置信度: %.1f%%".format(maxScore * 100)
        }

    }

    private fun convertBitmapToTensor(bitmap: Bitmap): OnnxTensor {
        val floatBuffer = preProcess(bitmap)
        return OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, 256, 256))
    }


}
}

// 修改后的ORTAnalyzer类，添加analyze方法以支持Bitmap输入
