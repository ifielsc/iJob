package com.ifelio.ijob.monitor.agent.controller;

import com.ifelio.ijob.contract.monitor.AgentInfo;
import com.ifelio.ijob.contract.monitor.MonitorCollectRequest;
import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.contract.monitor.PingResponse;
import com.ifelio.ijob.monitor.agent.service.MonitorCollectService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorCollectService service;

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse(service.ping());
    }

    @GetMapping("/info")
    public AgentInfo info() {
        return service.getAgentInfo();
    }

    @PostMapping("/collect")
    public MonitorReport collect(@RequestBody MonitorCollectRequest request) {
        return service.collect(request);
    }
}
