package com.krillsson.sysapi.core.forecast

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecastHistoryPoint
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

@Converter(autoApply = false)
@Component
class FileSystemSpaceForecastHistoryJsonConverter :
    AttributeConverter<List<FileSystemSpaceForecastHistoryPoint>?, String?> {

    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    override fun convertToDatabaseColumn(attribute: List<FileSystemSpaceForecastHistoryPoint>?): String? {
        return attribute?.let { mapper.writeValueAsString(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): List<FileSystemSpaceForecastHistoryPoint>? {
        return dbData?.let { mapper.readValue<List<FileSystemSpaceForecastHistoryPoint>>(it) }
    }
}
