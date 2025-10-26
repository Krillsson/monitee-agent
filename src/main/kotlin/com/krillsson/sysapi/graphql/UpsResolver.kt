package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.nut.NutUpsService
import com.krillsson.sysapi.nut.UpsDevice
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "UpsInfoAvailable")
class UpsResolver(val upsService: NutUpsService) {

    @SchemaMapping
    fun upsDevices(): List<UpsDevice> {
        return upsService.upsDevices()
    }

    @SchemaMapping
    fun upsDeviceById(@Argument id: String): UpsDevice? {
        return upsService.upsDeviceByName(id)
    }
}