package com.example.miniforum.controller;

import com.example.miniforum.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检测接口
 * <p>
 * 用于监控服务运行状态，返回服务、JVM 内存、运行时长等信息。
 * 该接口无需登录即可访问。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 简单健康检查：仅返回服务是否存活
     */
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("message", "服务运行正常");
        return Result.success(data);
    }

    /**
     * 详细健康检查：返回 JVM 内存、运行时长、线程数等运行信息
     */
    @GetMapping
    public Result<Map<String, Object>> health() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        Runtime runtime = Runtime.getRuntime();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "user-management");
        data.put("timestamp", System.currentTimeMillis());

        // 运行时长
        data.put("uptimeMs", runtimeMXBean.getUptime());
        data.put("uptimeSeconds", runtimeMXBean.getUptime() / 1000);

        // 内存信息
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("heapUsed", heap.getUsed());
        memory.put("heapCommitted", heap.getCommitted());
        memory.put("heapMax", heap.getMax());
        memory.put("nonHeapUsed", nonHeap.getUsed());
        memory.put("nonHeapCommitted", nonHeap.getCommitted());
        data.put("memory", memory);

        // 系统信息
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("availableProcessors", runtime.availableProcessors());
        system.put("totalMemory", runtime.totalMemory());
        system.put("freeMemory", runtime.freeMemory());
        system.put("maxMemory", runtime.maxMemory());
        data.put("system", system);

        // JVM 信息
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("name", runtimeMXBean.getVmName());
        jvm.put("version", runtimeMXBean.getVmVersion());
        jvm.put("vendor", runtimeMXBean.getVmVendor());
        data.put("jvm", jvm);

        return Result.success(data);
    }
}
