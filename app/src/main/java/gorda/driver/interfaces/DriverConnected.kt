package gorda.driver.interfaces

interface DriverConnected {
    var id: String
    var location: LocInterface
    var version: String
    var versionCode: Int
    var last_seen_at: Long
    var session_id: String
}
