// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OshiCollectorSupport {

    private final HardwareAbstractionLayer hardware;

    private final OperatingSystem operatingSystem;

    public CentralProcessor processor() {
        return hardware.getProcessor();
    }

    public GlobalMemory memory() {
        return hardware.getMemory();
    }

    public FileSystem fileSystem() {
        return operatingSystem.getFileSystem();
    }

    public List<OSProcess> processes() {
        //        return Arrays.asList(
        //                (OSProcess) operatingSystem.getProcesses(
        //                        null,
        //                        OperatingSystem.ProcessSorting.CPU_DESC,
        //                        0));

        return operatingSystem.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC, 0);
    }

    public List<NetworkIF> networkIFs() {

        List<NetworkIF> list = hardware.getNetworkIFs();

        list.forEach(NetworkIF::updateAttributes);

        return list;
    }
}
