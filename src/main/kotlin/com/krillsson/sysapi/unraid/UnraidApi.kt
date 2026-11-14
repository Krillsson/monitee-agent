package com.krillsson.sysapi.unraid

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

object UnraidApi {

    const val VM_DOMAINS_QUERY = "query { vms { domains { id name state } } }"

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VmDomain(
        @JsonProperty("id")
        val id: String?,
        @JsonProperty("name")
        val name: String?,
        @JsonProperty("state")
        val state: String?
    )
}
