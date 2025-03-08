/*
 * Sys-Api (https://github.com/Krillsson/sys-api)
 *
 * Copyright 2017 Christian Jensen / Krillsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Maintainers:
 * contact[at]christian-jensen[dot]se
 */
package com.krillsson.sysapi.config

data class YAMLConfigFile(
        val user: UserConfiguration,
        val metricsConfig: MetricsConfiguration,
        val windows: WindowsConfiguration,
        val processes: ProcessesConfiguration = ProcessesConfiguration(),
        val linux: LinuxConfiguration = LinuxConfiguration(),
        val connectivityCheck: ConnectivityCheckConfiguration,
        val updateCheck: UpdateCheckConfiguration = UpdateCheckConfiguration(),
        val docker: DockerConfiguration,
        val forwardHttpToHttps: Boolean,
        val logReader: LogReaderConfiguration = LogReaderConfiguration(),
        val selfSignedCertificates: SelfSignedCertificateConfiguration,
        val mDNS: MdnsConfiguration = MdnsConfiguration(false),
        val upnp: UpnpIgdConfiguration = UpnpIgdConfiguration(false)
)