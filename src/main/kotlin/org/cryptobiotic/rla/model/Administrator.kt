package org.cryptobiotic.rla.model

import java.time.Instant

data class Administrator(val id: Long,
                         val username: String,
                         val fullName: String,
                         val type: AdministratorType,
                         val county: County,
                         val version: Long) {

    var last_login_time: Instant? = null

    enum class AdministratorType {
        COUNTY, STATE
    }
}