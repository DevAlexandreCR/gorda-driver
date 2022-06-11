package gorda.driver.interfaces

import android.content.Intent

interface LocationUpdateInterface {
    fun onUpdate(intent: Intent)
}