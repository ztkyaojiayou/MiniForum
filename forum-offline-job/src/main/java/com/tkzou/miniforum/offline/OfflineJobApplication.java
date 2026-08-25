package com.tkzou.miniforum.offline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 离线作业运行器（forum-offline-job）
 * <p>
 * 独立进程：装载共享域（forum-core）+ 推荐管线（forum-recommend-server）+ 离线评估（recommend.eval），
 * 定时运行 {@code OfflineEvalScheduler}（行为数据不足跳过 → 时间切分评估 → 指标写日志 + eval-report）。
 * <p>
 * 无 web 控制器（RecommendController 归 demo-runner），纯离线批处理形态。
 */
@SpringBootApplication(scanBasePackages = "com.tkzou.miniforum")
@EnableScheduling
public class OfflineJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfflineJobApplication.class, args);
    }
}
