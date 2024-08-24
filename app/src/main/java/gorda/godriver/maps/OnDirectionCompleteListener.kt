package gorda.godriver.maps

interface OnDirectionCompleteListener {
    fun onSuccess(routes: ArrayList<Routes>): Unit
    fun onFailure(): Unit
}