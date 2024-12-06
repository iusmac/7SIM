package com.github.iusmac.sevensim.test

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

import org.hamcrest.Matchers.*

import org.junit.Assume.*

fun assumeDeviceWithRealRadioCapabilities() {
    assumeThat("Device does not provide any radio capabilities or is an emulator.",
        Build.getRadioVersion(), both(not(blankOrNullString())).and(not("1.0.0.0")))
}

fun setComponentEnabledSetting(context: Context, component: ComponentName, enabled: Boolean) {
    val pm = context.getPackageManager()
    val state = when(enabled) {
        true -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
}
