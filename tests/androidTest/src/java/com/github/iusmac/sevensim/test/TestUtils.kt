package com.github.iusmac.sevensim.test

import android.os.Build

import org.hamcrest.Matchers.*

import org.junit.Assume.*

fun assumeDeviceWithRealRadioCapabilities() {
    assumeThat("Device does not provide any radio capabilities or is an emulator.",
        Build.getRadioVersion(), both(not(blankOrNullString())).and(not("1.0.0.0")))
}
