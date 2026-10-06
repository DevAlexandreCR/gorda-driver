package gorda.driver.background

class MeterTicker(
    private val post: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS
) {

    companion object {
        private const val DEFAULT_INTERVAL_MS = 1000L
    }

    private var scheduled: Runnable? = null
    private var tick: (() -> Unit)? = null

    fun start(onTick: () -> Unit) {
        stop()
        tick = onTick
        val runnable = object : Runnable {
            override fun run() {
                tick?.invoke()
                post(this, intervalMs)
            }
        }
        scheduled = runnable
        post(runnable, intervalMs)
    }

    fun stop() {
        scheduled?.let { cancel(it) }
        scheduled = null
        tick = null
    }
}
