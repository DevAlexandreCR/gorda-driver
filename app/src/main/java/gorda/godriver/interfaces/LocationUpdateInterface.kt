package gorda.godriver.interfaces

import android.content.Intent

interface LocationUpdateInterface {
    fun onUpdate(intent: Intent)
}