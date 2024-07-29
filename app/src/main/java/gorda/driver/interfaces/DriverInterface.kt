package gorda.driver.interfaces

interface DriverInterface {
    var id: String
    var name: String
    var email: String
    var phone: String
    var docType: String
    var document: String
    var photoUrl: String
    var vehicle: Vehicle
    var enabled_at: Int
    var created_at: Int
    var device: Device?
}