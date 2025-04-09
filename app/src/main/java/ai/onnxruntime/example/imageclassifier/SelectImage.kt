package ai.onnxruntime.example.imageclassifier

import ai.onnxruntime.*
import ai.onnxruntime.example.imageclassifier.databinding.ActivitySelectImageBinding
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.impl.TagBundle


class SelectImage : AppCompatActivity() {
    private lateinit var binding: ActivitySelectImageBinding
    public val TAG = "SelectImage"
    private val PICK_IMAGE_REQUEST = 1

    private val backgroundExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private val labelData: List<String> by lazy { readLabels() }
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private lateinit var ortEnv: OrtEnvironment
    private  lateinit var ortSession: OrtSession
    private  var ortAnalyzer: ORTAnalyzer? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val initializationLock = Any()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化日志记录：记录Activity创建事件
        Log.d(TAG, "SelectImage Activity created")
        // 视图绑定初始化：通过DataBinding加载布局资源
        binding = ActivitySelectImageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ortEnv = OrtEnvironment.getEnvironment()
        scope.launch {
            try{
                val session = createOrtSession()
                synchronized(initializationLock){
                    ortSession = session
                    ortAnalyzer = ORTAnalyzer(ortSession, ::updateUI).also {
                        Log.d(TAG, "ORT analyzer created")
                    }
                }
                Log.d(TAG, "ORT environment created")

            }catch (e: Exception){
                Log.e(TAG, "ORT environment creation failed", e)
            }
        }
        // 图片选择按钮点击事件：触发打开系统相册并记录操作日志
        binding.imageView.setOnClickListener({
            Log.d(TAG, "SelectImage clicked")
            openGallery()
            setORTAnalyzer()
        })
        // 边缘到边缘显示配置：启用系统栏区域内容延伸
        enableEdgeToEdge()
        // 窗口边距监听器：根据系统栏尺寸动态调整视图内边距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setORTAnalyzer() {
        scope.launch {
            imageAnalysis?.clearAnalyzer()
            synchronized(initializationLock){
                imageAnalysis?.setAnalyzer(
                    backgroundExecutor,
                    ortAnalyzer?:return@launch
                )
            }
        }
    }

    fun openGallery() {
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
                scope.launch {
                    ortAnalyzer?.analyzeBitmap(resizedBitmap)?:run {
                        Log.e(TAG, "Failed to analyze image")
                        binding.analyzeItem.text = "未知类别"
                        binding.analyzeResult.text = "置信度: 0%"
                    }
                }

            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        ortAnalyzer?.finalize() // 确保释放ORT资源
        ortEnv?.close()
        backgroundExecutor.shutdown()
    }
    fun readLabels(): List<String> {
        return resources.openRawResource(R.raw.classify).bufferedReader().readLines()
    }
    suspend fun readModels(): ByteArray = withContext(Dispatchers.IO) {
        resources.openRawResource(R.raw.resnet50_populus).readBytes()
    }
    suspend fun createOrtSession(): OrtSession = withContext(Dispatchers.Default) {
        ortEnv.createSession(readModels()) ?: throw IllegalStateException("Failed to create ORT session")
    }

    fun updateUI(result: Result) {
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



}


// 修改后的ORTAnalyzer类，添加analyze方法以支持Bitmap输入
