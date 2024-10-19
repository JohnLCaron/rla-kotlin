package org.cryptobiotic.rla.persistence

object CountyQueries {

    fun getName(countyId: Long): String {
        return "nameFor$countyId"
    }
}