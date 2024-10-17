package org.cryptobiotic.rla.csv

/** The result of parsing/importing a csv file  */
class Result {
    var success: Boolean = false
    var importedCount: Int? = null
    var errorMessage: String? = null
    var errorRowNum: Int? = null
    var errorRowContent: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Result

        if (success != other.success) return false
        if (importedCount != other.importedCount) return false
        if (errorMessage != other.errorMessage) return false
        if (errorRowNum != other.errorRowNum) return false
        if (errorRowContent != other.errorRowContent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + (importedCount ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + (errorRowNum ?: 0)
        result = 31 * result + (errorRowContent?.hashCode() ?: 0)
        return result
    }


}
