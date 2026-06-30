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
package com.krillsson.sysapi.core.domain.processes

import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import oshi.software.os.OSProcess

class Process(
    val name: String,
    val path: String,
    val commandLine: String,
    val user: String,
    val userID: String,
    val group: String,
    val groupID: String,
    val state: OSProcess.State,
    val processID: Int,
    val parentProcessID: Int,
    val threadCount: Int,
    val priority: Int,
    val virtualSize: Long,
    val residentSetSize: Long,
    val memoryPercent: Double,
    val kernelTime: Long,
    val userTime: Long,
    val upTime: Long,
    val cpuPercent: Double,
    val startTime: Long,
    val bytesRead: Long,
    val bytesWritten: Long
) {

    companion object {
        @kotlin.jvm.JvmStatic
        fun create(
            process: OSProcess,
            memory: MemoryLoad
        ): Process {
            return Process(
                process.name,
                process.path,
                process.commandLine,
                process.user,
                process.userID,
                process.group,
                process.groupID,
                process.state,
                process.processID,
                process.parentProcessID,
                process.threadCount,
                process.priority,
                process.virtualSize,
                process.privateResidentMemory,
                100.0 * process.privateResidentMemory / memory.totalBytes,
                process.kernelTime, process.userTime,
                process.upTime,
                100.0 * (process.kernelTime + process.userTime) / process.upTime,
                process.startTime,
                process.bytesRead,
                process.bytesWritten
            )
        }
    }
}