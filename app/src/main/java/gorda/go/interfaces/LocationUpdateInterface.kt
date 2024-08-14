package gorda.go.interfaces

import android.content.Intent

interface LocationUpdateInterface {
    fun onUpdate(intent: Intent)
}