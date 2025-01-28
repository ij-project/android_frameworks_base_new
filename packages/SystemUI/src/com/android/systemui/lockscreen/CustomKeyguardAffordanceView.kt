/*
     Copyright (C) 2024 the risingOS Android Project
     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
*/
package com.android.systemui.lockscreen

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.Toast

import androidx.core.content.ContextCompat

import com.android.systemui.Dependency
import com.android.systemui.res.R
import com.android.systemui.animation.view.LaunchableImageView
import com.android.systemui.statusbar.policy.FlashlightController

class CustomKeyguardAffordanceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LaunchableImageView(context, attrs, defStyleAttr) {

    private var isLongPress = false
    private var longPressHandler: Handler? = null
    private var settingsObserver: ContentObserver? = null

    private var shortcutKey: String = ""
    private var isCustomAppEnabledLeft: Boolean = false
    private var isCustomAppEnabledRight: Boolean = false
    private var customAppLeft: String? = null
    private var customAppRight: String? = null
    
    private val activityLauncherUtils = ActivityLauncherUtils(context)
    private val mController: CustomKeyguardAffordanceViewController = CustomKeyguardAffordanceViewController.getInstance()
    
    private var mCameraManager: CameraManager? = null
    private var mCameraId: String? = null
    private var isFlashOn = false

    private val mFlashlightController: FlashlightController = Dependency.get(FlashlightController::class.java)
    private var mFlashlightCallbackRegistered = false

    private val affordanceUris = listOf(
        "keyguard_shortcut_left",
        "keyguard_shortcut_right",
        "keyguard_shortcut_custom_app_left",
        "keyguard_shortcut_custom_app_right"
    )

    init {
        background = ContextCompat.getDrawable(context, R.drawable.keyguard_bottom_affordance_bg)
        setOnTouchListener { _, event -> handleTouchEvent(event) }
        mCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            mCameraId = mCameraManager?.cameraIdList?.get(0)
        } catch (e: Exception) {}
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        shortcutKey = when (id) {
            R.id.start_shortcut -> "keyguard_shortcut_left"
            R.id.end_shortcut -> "keyguard_shortcut_right"
            else -> ""
        }

        settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                updateSettings()
            }
        }

        affordanceUris.forEach { uri ->
            context.contentResolver.registerContentObserver(Settings.System.getUriFor(uri), false, settingsObserver!!)
        }

        updateSettings()
        mController.addAffordanceView(this)
        backgroundTintList = ColorStateList.valueOf(Color.parseColor("#99000000"))
        updateIconTint()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        settingsObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
        }
        longPressHandler = null
        mController.removeAffordanceView(this)
    }

    fun updateIconTint() {
        val shortcutValue = getShortcutValue()
        if (shortcutValue == "custom_app") {
            val isLeftKey = shortcutKey == "keyguard_shortcut_left"
            val isRightKey = shortcutKey == "keyguard_shortcut_right"
            val customAppKeyNotEmpty = if (isLeftKey) {
                !customAppLeft.isNullOrEmpty()
            } else if (isRightKey) {
                !customAppRight.isNullOrEmpty()
            } else {
                false
            }
            if (customAppKeyNotEmpty) {
                clearColorFilter()
            } else {
                setColorFilter(Color.WHITE)
            }
        } else {
            setColorFilter(Color.WHITE)
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isLongPress = false
                startScalingAnimation()
                longPressHandler = Handler(Looper.getMainLooper())
                longPressHandler?.postDelayed({
                    isLongPress = true
                    handleShortcutTask()
                }, 800)
                if (!isLongPress) {
                    startShakeAnimation()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopScalingAnimation()
                stopShakeAnimation()
                longPressHandler?.removeCallbacksAndMessages(null)
                return true
            }
        }
        return false
    }

    private fun startShakeAnimation() {
        animate().translationX(10f).setDuration(50)
            .withEndAction {
                animate().translationX(-10f).setDuration(50)
                    .withEndAction {
                        animate().translationX(10f).setDuration(50)
                            .withEndAction {
                                animate().translationX(0f).setDuration(50)
                            }
                    }
            }
    }

    private fun stopShakeAnimation() {
        animate().translationX(0f).setDuration(200).start()
    }

    private fun startScalingAnimation() {
        animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start()
    }

    private fun stopScalingAnimation() {
        animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun updateSettings() {
        val resolver = context.contentResolver
        customAppLeft = Settings.System.getString(resolver, "keyguard_shortcut_custom_app_left")
        customAppRight = Settings.System.getString(resolver, "keyguard_shortcut_custom_app_right")
        updateShortcutIcon()

        val shortcutValue = getShortcutValue()
        visibility = if (shortcutValue == "none" || shortcutValue.isNullOrEmpty()) GONE else VISIBLE

        if (shortcutValue == "flashlight" && !mFlashlightCallbackRegistered) {
            mFlashlightController.addCallback(mFlashlightCallback)
            mFlashlightCallbackRegistered = true
        } else if (shortcutValue != "flashlight" && mFlashlightCallbackRegistered) {
            mFlashlightController.removeCallback(mFlashlightCallback)
            mFlashlightCallbackRegistered = false
        }
    }

    private fun updateShortcutIcon() {
        val shortcutValue = getShortcutValue()
        val appPackage = when (shortcutKey) {
            "keyguard_shortcut_left" -> if (shortcutValue == "custom_app") customAppLeft else null
            "keyguard_shortcut_right" -> if (shortcutValue == "custom_app") customAppRight else null
            else -> null
        }

        val icon = if (appPackage != null) {
            getCustomAppIcon(appPackage)
        } else {
            getShortcutIcon(shortcutValue)
        }

        setImageDrawable(icon)
        updateIconTint()
    }

    private fun handleShortcutTask() {
        val shortcutValue = getShortcutValue()
        val appPackage = when (shortcutKey) {
            "keyguard_shortcut_left" -> if (shortcutValue == "custom_app") customAppLeft else null
            "keyguard_shortcut_right" -> if (shortcutValue == "custom_app") customAppRight else null
            else -> null
        }

        if (appPackage != null) {
            activityLauncherUtils.launchCustomApp(appPackage)
        } else {
            when (shortcutValue) {
                "camera" -> activityLauncherUtils.launchCamera()
                "flashlight" -> toggleFlashlight()
                "mute" -> mutePhone()
                "qr_scanner" -> activityLauncherUtils.launchQrScanner()
                "wallet" -> activityLauncherUtils.launchWalletApp()
                else -> activityLauncherUtils.launchCustomApp(shortcutValue)
            }
        }
        com.android.internal.util.android.VibrationUtils.triggerVibration(context, 4)
    }

    private fun getShortcutValue(): String {
        return Settings.System.getString(context.contentResolver, shortcutKey) ?: when (shortcutKey) {
            "keyguard_shortcut_right" -> "qr_scanner"
            else -> "camera"
        }
    }

    fun toggleFlashlight() {
        try {
            mCameraManager?.setTorchMode(mCameraId!!, !isFlashOn)
            isFlashOn = !isFlashOn
        } catch (e: Exception) {}
        updateAffordanceViewState(isFlashOn)
    }

    private fun mutePhone() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            Toast.makeText(context, "Phone unmuted", Toast.LENGTH_SHORT).show()
        } else {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            Toast.makeText(context, "Phone silenced", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getShortcutIcon(shortcutValue: String): Drawable? {
        return when (shortcutValue) {
            "camera" -> ContextCompat.getDrawable(context, R.drawable.ic_camera)
            "flashlight" -> ContextCompat.getDrawable(context, R.drawable.qs_flashlight_icon_off)
            "mute" -> ContextCompat.getDrawable(context, R.drawable.ic_notifications_silence)
            "qr_scanner" -> ContextCompat.getDrawable(context, R.drawable.ic_qr_code_scanner)
            "wallet" -> ContextCompat.getDrawable(context, R.drawable.ic_wallet_lockscreen)
            else -> null
        }
    }

    private fun getCustomAppIcon(packageName: String?): Drawable? {
        return try {
            packageName?.let { context.packageManager.getApplicationIcon(it) }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    private fun affordanceSupportsStates(): Boolean {
        return when (getShortcutValue()) {
            "flashlight" -> true
            else -> false
        }
    }
    
    private fun updateAffordanceViewState(active: Boolean) {
        if (!affordanceSupportsStates()) return
        val bgColor = if (active) Color.WHITE else Color.parseColor("#99000000")
        val tintColor = if (active) Color.parseColor("#99000000") else Color.WHITE
        backgroundTintList = ColorStateList.valueOf(bgColor)
        setColorFilter(tintColor)
    }
    
    private val mFlashlightCallback = object : FlashlightController.FlashlightListener {
        override fun onFlashlightChanged(enabled: Boolean) {
            isFlashOn = enabled
            updateAffordanceViewState(isFlashOn)
        }
        override fun onFlashlightError() {}
        override fun onFlashlightAvailabilityChanged(available: Boolean) {
            isFlashOn = mFlashlightController.isEnabled && available
            updateAffordanceViewState(isFlashOn)
        }
    }
}
