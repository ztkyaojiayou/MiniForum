package com.tkzou.miniforum.recommend.eval;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * XXL-Job 执行器 · 离线端（生产适配，@Profile("prod") 激活）
 * <p>
 * 离线评估任务由调度中心（xxl-job-admin）派发给本执行器（appname mini-forum-offline），
 * 演示（!prod）不加载本类，仍由 @Scheduled 自调度（app.scheduling.mode=local）。
 */
@Configuration
@Profile("prod")
public class XxlJobOfflineConfig {

    private final OfflineEvalScheduler offlineEvalScheduler;

    public XxlJobOfflineConfig(OfflineEvalScheduler offlineEvalScheduler) {
        this.offlineEvalScheduler = offlineEvalScheduler;
    }

    @Value("${xxl.job.admin.addresses:http://localhost:8080/xxl-job-admin}")
    private String adminAddresses;
    @Value("${xxl.job.accessToken:}")
    private String accessToken;
    @Value("${xxl.job.executor.appname:mini-forum-offline}")
    private String appname;
    @Value("${xxl.job.executor.port:9998}")
    private int port;
    @Value("${xxl.job.executor.logpath:./logs/xxl-job}")
    private String logPath;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath(logPath);
        return executor;
    }

    /** 定时离线评估（对应原 OfflineEvalScheduler.runEval） */
    @XxlJob("offline-eval")
    public void offlineEval(String param) {
        offlineEvalScheduler.doRunEval();
    }
}
