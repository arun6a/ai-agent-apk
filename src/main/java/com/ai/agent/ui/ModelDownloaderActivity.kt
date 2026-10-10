package com.ai.agent.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ai.agent.llm.LocalLLM
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class ModelDownloaderActivity : AppCompatActivity() {

    private lateinit var localLLM: LocalLLM
    private lateinit var layout: LinearLayout
    private var downloadProgress: ProgressBar? = null
    private var downloadStatus: TextView? = null

    companion object {
        private const val PICK_GGUF_FILE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localLLM = LocalLLM(this)

        val scrollView = ScrollView(this)
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "Local LLM Models"
            textSize = 24f
            setTextColor(0xFFFAFAFA.toInt())
            setPadding(0, 0, 0, 16)
        })

        // Info
        layout.addView(TextView(this).apply {
            text = "Use AI completely offline. No internet, no server, no rate limits.\n\n" +
                   "Two ways to add a model:\n" +
                   "  1. Import a .gguf file you already have (fastest)\n" +
                   "  2. Download a recommended model below\n\n" +
                   "Recommended: Qwen 2.5 0.5B (fastest) or 1.5B (smarter)"
            textSize = 13f
            setTextColor(0xFFA1A1AA.toInt())
            setPadding(0, 0, 0, 24)
        })

        // Binary status
        if (localLLM.isBinaryAvailable()) {
            layout.addView(TextView(this).apply {
                text = "✅ llama.cpp engine ready (bundled in app)"
                textSize = 13f
                setTextColor(0xFF10B981.toInt())
                setPadding(0, 8, 0, 16)
            })
        }

        // === IMPORT FILE BUTTON (prominent, at top) ===
        val importBtn = Button(this).apply {
            text = "📂 Import .gguf file from phone"
            setOnClickListener {
                openFilePicker()
            }
        }
        layout.addView(importBtn)

        layout.addView(TextView(this).apply {
            text = "Pick any .gguf model from your Downloads or files.\n" +
                   "It will be copied to the app and set as active."
            textSize = 11f
            setTextColor(0xFF71717A.toInt())
            setPadding(0, 4, 0, 24)
        })

        // Downloaded Models section
        layout.addView(TextView(this).apply {
            text = "Your Models"
            textSize = 18f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 16, 0, 8)
        })

        val downloaded = localLLM.getDownloadedModels()
        if (downloaded.isEmpty()) {
            layout.addView(TextView(this).apply {
                text = "No models yet. Import a .gguf file or download one below."
                textSize = 13f
                setTextColor(0xFF52525B.toInt())
                setPadding(0, 0, 0, 16)
            })
        } else {
            for (model in downloaded) {
                val isActive = model.name == (localLLM.getActiveModel()?.name ?: "")
                layout.addView(createModelRow(model.name, "${model.length() / 1048576} MB", isActive) {
                    localLLM.setActiveModel(model.name)
                    Toast.makeText(this, "Active model: ${model.name}", Toast.LENGTH_SHORT).show()
                    recreate()
                })
            }
        }

        // Available models
        layout.addView(TextView(this).apply {
            text = "Download Models"
            textSize = 18f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 24, 0, 8)
        })

        for (model in LocalLLM.RECOMMENDED_MODELS) {
            val isDownloaded = localLLM.isModelDownloaded(model.name)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(0xFF27272A.toInt())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 12
                layoutParams = params
            }

            card.addView(TextView(this).apply {
                text = model.name
                textSize = 15f
                setTextColor(0xFFFAFAFA.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            card.addView(TextView(this).apply {
                text = "Size: ${model.size} | RAM: ${model.ramNeeded}"
                textSize = 12f
                setTextColor(0xFF10B981.toInt())
                setPadding(0, 4, 0, 4)
            })

            card.addView(TextView(this).apply {
                text = model.description
                textSize = 12f
                setTextColor(0xFFA1A1AA.toInt())
                setPadding(0, 4, 0, 8)
            })

            val btn = Button(this).apply {
                text = if (isDownloaded) "✓ Downloaded" else "Download"
                isEnabled = !isDownloaded
                setOnClickListener {
                    if (!isDownloaded) {
                        downloadModel(model)
                    }
                }
            }
            card.addView(btn)

            layout.addView(card)
        }

        // Download progress
        downloadProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(downloadProgress)

        downloadStatus = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(0xFF10B981.toInt())
            visibility = View.GONE
        }
        layout.addView(downloadStatus)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    /**
     * Open Android's file picker to let user select a .gguf file.
     */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/octet-stream"
            // Try to filter for .gguf files
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream",
                "application/x-gguf",
                "application/octet-stream"
            ))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "Select a .gguf model file"), PICK_GGUF_FILE)
        } catch (e: Exception) {
            // Fallback: use ACTION_OPEN_DOCUMENT
            val altIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(altIntent, PICK_GGUF_FILE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_GGUF_FILE) {
            if (resultCode == Activity.RESULT_OK && data?.data != null) {
                val uri = data.data!!
                importModel(uri)
            } else {
                Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Copy the selected .gguf file into the app's models directory.
     */
    private fun importModel(uri: Uri) {
        downloadProgress?.visibility = View.VISIBLE
        downloadStatus?.visibility = View.VISIBLE
        downloadStatus?.text = "Importing model..."
        downloadProgress?.progress = 0
        downloadProgress?.isIndeterminate = true

        thread {
            try {
                // Get filename from URI (fallback to "imported_model.gguf")
                var fileName = uri.lastPathSegment ?: "imported_model.gguf"
                // Clean up filename — content URIs have weird paths
                fileName = fileName.substringAfterLast("/")
                if (!fileName.endsWith(".gguf", ignoreCase = true)) {
                    fileName = "$fileName.gguf"
                }
                if (fileName.isBlank() || fileName == ".gguf") {
                    fileName = "imported_model.gguf"
                }

                val targetFile = File(localLLM.getModelsDir(), fileName)
                android.util.Log.i("ModelDownloader", "Importing $uri -> ${targetFile.absolutePath}")

                runOnUiThread {
                    downloadStatus?.text = "Copying $fileName..."
                }

                // Copy file via ContentResolver
                val input = contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot read file")
                input.use { ins ->
                    FileOutputStream(targetFile).use { out ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (ins.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalRead % (1024 * 1024) == 0L) {  // Update every 1MB
                                runOnUiThread {
                                    downloadStatus?.text = "Copying... ${totalRead / 1048576} MB"
                                }
                            }
                        }
                    }
                }

                // Verify it's a valid file (non-empty)
                if (targetFile.length() < 1000) {
                    targetFile.delete()
                    runOnUiThread {
                        downloadStatus?.text = "Error: File too small to be a valid model"
                        Toast.makeText(this, "Invalid model file", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }

                // Set as active model
                localLLM.setActiveModel(fileName)

                runOnUiThread {
                    downloadProgress?.isIndeterminate = false
                    downloadProgress?.progress = 100
                    downloadStatus?.text = "✓ Imported ${fileName} (${targetFile.length() / 1048576} MB)!"
                    Toast.makeText(this, "Model imported and set as active!", Toast.LENGTH_LONG).show()

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        recreate()
                    }, 1500)
                }
            } catch (e: Exception) {
                android.util.Log.e("ModelDownloader", "Import failed", e)
                runOnUiThread {
                    downloadProgress?.isIndeterminate = false
                    downloadStatus?.text = "Error: ${e.message}"
                    Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createModelRow(name: String, size: String, isActive: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xFF27272A.toInt())
        }

        row.addView(TextView(this).apply {
            text = if (isActive) "★ $name" else "  $name"
            textSize = 14f
            setTextColor(if (isActive) 0xFF10B981.toInt() else 0xFFFAFAFA.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(TextView(this).apply {
            text = size
            textSize = 12f
            setTextColor(0xFFA1A1AA.toInt())
        })

        if (!isActive) {
            row.addView(Button(this).apply {
                text = "Use"
                setOnClickListener { onClick() }
            })
        }

        return row
    }

    private fun downloadModel(model: LocalLLM.ModelInfo) {
        downloadProgress?.visibility = View.VISIBLE
        downloadStatus?.visibility = View.VISIBLE
        downloadStatus?.text = "Downloading ${model.name}..."
        downloadProgress?.progress = 0
        downloadProgress?.isIndeterminate = false

        thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(model.url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    runOnUiThread {
                        downloadStatus?.text = "Download failed: ${response.code}"
                        Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }

                val fileName = model.url.substringAfterLast("/")
                val outputFile = File(localLLM.getModelsDir(), fileName)
                val body = response.body ?: return@thread
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) {
                                val percent = (totalRead * 100 / totalBytes).toInt()
                                runOnUiThread {
                                    downloadProgress?.progress = percent
                                    downloadStatus?.text = "Downloading... $percent% (${totalRead / 1048576}MB / ${totalBytes / 1048576}MB)"
                                }
                            }
                        }
                    }
                }

                localLLM.setActiveModel(fileName)

                runOnUiThread {
                    downloadProgress?.progress = 100
                    downloadStatus?.text = "✓ Downloaded ${model.name} successfully!"
                    Toast.makeText(this, "Model downloaded! Set as active.", Toast.LENGTH_LONG).show()

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        recreate()
                    }, 1500)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    downloadStatus?.text = "Error: ${e.message}"
                    Toast.makeText(this, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
