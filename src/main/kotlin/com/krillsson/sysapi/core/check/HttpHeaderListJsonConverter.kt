package com.krillsson.sysapi.core.check

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

@Converter(autoApply = false)
@Component
class HttpHeaderListJsonConverter : AttributeConverter<List<HttpHeader>?, String?> {
    private val mapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: List<HttpHeader>?): String? {
        return attribute?.let { mapper.writeValueAsString(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): List<HttpHeader>? {
        return dbData?.let { mapper.readValue<List<HttpHeader>>(it) }
    }
}
