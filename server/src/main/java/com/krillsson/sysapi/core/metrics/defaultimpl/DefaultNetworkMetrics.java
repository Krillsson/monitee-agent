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
package com.krillsson.sysapi.core.metrics.defaultimpl;

import com.krillsson.sysapi.core.domain.network.NetworkInterface;
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad;
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceSpeed;
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceValues;
import com.krillsson.sysapi.core.metrics.NetworkMetrics;
import com.krillsson.sysapi.core.speed.SpeedMeasurementManager;
import org.slf4j.Logger;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.net.SocketException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultNetworkMetrics implements NetworkMetrics {

    protected static final NetworkInterfaceSpeed EMPTY_INTERFACE_SPEED = new NetworkInterfaceSpeed(0, 0);
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DefaultNetworkMetrics.class);
    private static final int BYTE_TO_BIT = 8;
    private final HardwareAbstractionLayer hal;
    private final SpeedMeasurementManager speedMeasurementManager;

    public DefaultNetworkMetrics(HardwareAbstractionLayer hal, SpeedMeasurementManager speedMeasurementManager) {
        this.hal = hal;
        this.speedMeasurementManager = speedMeasurementManager;
    }

    void register() {
        List<SpeedMeasurementManager.SpeedSource> collect = hal.getNetworkIFs()
                .stream()
                .map(n -> new SpeedMeasurementManager.SpeedSource() {
                    @Override
                    public String getName() {
                        return n.getName();
                    }

                    @Override
                    public long getCurrentRead() {
                        n.updateAttributes();
                        return n.getBytesRecv();
                    }

                    @Override
                    public long getCurrentWrite() {
                        //TODO: maybe it's good enough to do this in getCurrentRead since getCurrentWrite is called immediately after
                        n.updateAttributes();
                        return n.getBytesSent();
                    }
                })
                .collect(Collectors.toList());
        speedMeasurementManager.register(collect);
    }

    @Override
    public List<NetworkInterface> networkInterfaces() {
        return hal.getNetworkIFs().stream().map(mapToNetworkInterface()).collect(Collectors.toList());
    }

    @Override
    public Optional<NetworkInterface> networkInterfaceById(String id) {
        return hal.getNetworkIFs().stream().filter(n -> n.getName().equalsIgnoreCase(id)).map(
                mapToNetworkInterface()).findAny();
    }

    @Override
    public List<NetworkInterfaceLoad> networkInterfaceLoads() {
        return hal.getNetworkIFs().stream()
                .map(mapToLoad())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<NetworkInterfaceLoad> networkInterfaceLoadById(String id) {
        return hal.getNetworkIFs().stream()
                .filter(n -> n.getName().equalsIgnoreCase(id))
                .map(mapToLoad())
                .findAny();
    }


    protected NetworkInterfaceSpeed speedForInterfaceWithName(String name) {
        Optional<SpeedMeasurementManager.CurrentSpeed> currentSpeedForName = speedMeasurementManager.getCurrentSpeedForName(
                name);
        return currentSpeedForName.map(s -> new NetworkInterfaceSpeed(
                s.getReadPerSeconds(),
                s.getWritePerSeconds()
        )).orElse(EMPTY_INTERFACE_SPEED);
    }


    Function<NetworkIF, NetworkInterface> mapToNetworkInterface() {
        return nic -> {
            boolean loopback = false;
            try {
                loopback = nic.queryNetworkInterface().isLoopback();
            } catch (SocketException e) {
                //ignore
                LOGGER.warn("Socket exception while queering for loopback parameter", e);
            }
            return new NetworkInterface(
                    nic.getName(),
                    nic.getDisplayName(),
                    nic.getMacaddr(),
                    nic.getSpeed(), nic.getMTU(),
                    loopback,
                    Stream.of(nic.getIPv4addr()).collect(Collectors.toList()),
                    Stream.of(nic.getIPv6addr()).collect(Collectors.toList())
            );
        };
    }

    private Function<NetworkIF, NetworkInterfaceLoad> mapToLoad() {
        return n -> {
            boolean up = false;
            try {
                up = n.queryNetworkInterface().isUp();
            } catch (SocketException e) {
                LOGGER.error("Error occurred while getting status for NIC", e);
            }
            return new NetworkInterfaceLoad(
                    n.getName(), up,
                    new NetworkInterfaceValues(
                            n.getSpeed(),
                            n.getBytesRecv(),
                            n.getBytesSent(),
                            n.getPacketsRecv(),
                            n.getPacketsSent(),
                            n.getInErrors(),
                            n.getOutErrors()
                    ),
                    speedForInterfaceWithName(n.getName())
            );
        };
    }
}
