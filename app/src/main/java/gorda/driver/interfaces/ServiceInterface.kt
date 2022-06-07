package gorda.driver.interfaces

interface ServiceInterface {
    var id: String?
    var status: String
    var start_address: String
    var end_address: String?
    var phone: String
    var name: String
    var comment: String?
    var amount: Int?
    var driver_id: String?
    var client_id: String?
    var created_at: Long
}