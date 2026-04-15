package gorda.driver.exceptions

class UnsupportedAppVersionException :
    IllegalStateException("App version is no longer supported for online connections")
