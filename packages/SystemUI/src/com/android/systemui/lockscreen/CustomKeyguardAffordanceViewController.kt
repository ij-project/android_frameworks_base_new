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
import android.view.View

import com.android.systemui.Dependency
import com.android.systemui.statusbar.policy.ConfigurationController

import java.util.concurrent.CopyOnWriteArraySet

class CustomKeyguardAffordanceViewController private constructor(): ConfigurationController.ConfigurationListener {

    private val affordanceViews: MutableSet<CustomKeyguardAffordanceView> = CopyOnWriteArraySet()

    private val configurationController: ConfigurationController = Dependency.get(ConfigurationController::class.java)

    private lateinit var mContext: Context

    fun addAffordanceView(view: CustomKeyguardAffordanceView) {
        if (affordanceViews.isEmpty()) {
            mContext = view.context.applicationContext
            configurationController.addCallback(this)
        }
        affordanceViews.add(view)
    }

    fun removeAffordanceView(view: CustomKeyguardAffordanceView) {
        affordanceViews.remove(view)
        if (affordanceViews.isEmpty()) {
            configurationController.removeCallback(this)
        }
    }

    fun setAlpha(alpha: Float) {
        affordanceViews.forEach {
            it.post { it.alpha = alpha }
        }
    }

    fun hideAffordanceViews() {
        affordanceViews.forEach {
            it.visibility = View.GONE
        }
    }

    fun showAffordanceViews() {
        affordanceViews.forEach {
            it.visibility = View.VISIBLE
        }
    }

    override fun onUiModeChanged() {
        updateIconTint()
    }

    override fun onThemeChanged() {
        updateIconTint()
    }

    private fun updateIconTint() {
        affordanceViews.forEach {
            it.post { it.updateIconTint() }
        }
    }

    companion object {
        @Volatile
        private var instance: CustomKeyguardAffordanceViewController? = null

        fun getInstance(): CustomKeyguardAffordanceViewController {
            return instance ?: synchronized(this) {
                instance ?: CustomKeyguardAffordanceViewController().also { instance = it }
            }
        }
    }
}
