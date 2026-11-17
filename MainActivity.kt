package com.example.screenlens

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.screenlens.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var projectionManager: MediaProjectionManager

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "تم منح الإذن", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "الإذن مطلوب للتشغيل", Toast.LENGTH_LONG).show()
            }
        }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val intent = Intent(this, ScreenLensService::class.java).apply {
                    action = ScreenLensService.ACTION_START
                    putExtra(ScreenLensService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenLensService.EXTRA_RESULT_DATA, result.data)
                }
                startForegroundService(intent)
                binding.tvStatus.text = "الحالة: العدسة نشطة (الزر العائم ظاهر)"
            } else {
                Toast.makeText(this, "تم إلغاء التقاط الشاشة", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.btnOverlayPermission.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                overlayPermissionLauncher.launch(intent)
            } else {
                Toast.makeText(this, "الإذن ممنوح مسبقاً", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "يجب منح إذن الرسم فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // طلب التقاط الشاشة (MediaProjection)
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(this, ScreenLensService::class.java).apply {
                action = ScreenLensService.ACTION_STOP
            }
            startService(intent)
            binding.tvStatus.text = "الحالة: غير نشطة"
        }
    }
}
