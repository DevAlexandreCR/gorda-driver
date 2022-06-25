package gorda.driver.interfaces

interface ServiceInterface {
    var id: String?
    var status: String
    val start_loc: LocType
    var end_loc: LocType?
    var phone: String
    var name: String
    var comment: String?
    var amount: Int?
    var driver_id: String?
    var client_id: String?
    var created_at: Long
    var metadata: ServiceMetadata
}