package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.ups.UpsDevice
import com.krillsson.sysapi.ups.UpsService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "UpsInfoAvailable")
class UpsResolver(val upsService: UpsService) {

    @SchemaMapping
    fun upsDevices(): List<UpsDevice> {
        return upsService.upsDevices()
    }

    @SchemaMapping
    fun upsDeviceById(@Argument id: String): UpsDevice? {
        return upsService.upsDeviceByName(id)
    }
}