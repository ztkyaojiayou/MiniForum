package com.tkzou.miniforum.recommend.prod.xxl;

import com.tkzou.miniforum.recommend.coldstart.TrafficPool;
import com.tkzou.miniforum.recommend.prod.mysql.MySqlDataStore;
import com.tkzou.miniforum.service.PostService;
import com.tkzou.miniforum.service.SimulatedActivityService;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * XXL-Job 执行器 · Web 端（生产适配，@Profile("prod") 激活，需 -Pprod 编译）
 * <p>
 * 中心化调度：调度中心（xxl-job-admin）把任务派发给本执行器，业务方法由 {@code @XxlJob} handler 调用；
 * 演示（!prod）不加载本类，任务仍由 @Scheduled 自调度（app.scheduling.mode=local，各业务类的 @Scheduled
 * 在 xxl 模式下空转防双跑）。
 * <p>
 * 对应任务须在 xxl-job-admin 配置与 handler 同名的 JobHandler（appname 认领执行器）。
 */
@Configuration
@Profile("prod")
public class XxlJobWebConfig {

    private final SimulatedActivityService simulatedActivityService;
    private final PostService postService;
    private final TrafficPool trafficPool;
    private final MySqlDataStore mySqlDataStore;

    public XxlJobWebConfig(SimulatedActivityService simulatedActivityService,
                           PostService postService,
                           TrafficPool trafficPool,
                           MySqlDataStore mySqlDataStore) {
        this.simulatedActivityService = simulatedActivityService;
        this.postService = postService;
        this.trafficPool = trafficPool;
        this.mySqlDataStore = mySqlDataStore;
    }

    @Value("${xxl.job.admin.addresses:http://localhost:8080/xxl-job-admin}")
    private String adminAddresses;
    @Value("${xxl.job.accessToken:}")
    private String accessToken;
    @Value("${xxl.job.executor.appname:mini-forum-web}")
    private String appname;
    @Value("${xxl.job.executor.port:9999}")
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

    /** 模拟活动：定时造帖/互动（对应原 SimulatedActivityService.simulate） */
    @XxlJob("sim-activity")
    public void simActivity(String param) {
        simulatedActivityService.doSimulate();
    }

    /** 回收站清理：彻底删除过期软删帖并级联（对应原 PostService.purgeExpiredPosts） */
    @XxlJob("recycle-purge")
    public void recyclePurge(String param) {
        postService.doPurgeExpiredPosts();
    }

    /** 流量池清理：清理停止探索超 7 天的赛马状态（对应原 TrafficPool.cleanup） */
    @XxlJob("traffic-pool-cleanup")
    public void trafficPoolCleanup(String param) {
        trafficPool.doCleanup();
    }

    /** MySQL 快照持久化（对应原 MySqlDataStore.scheduledSave） */
    @XxlJob("mysql-snapshot")
    public void mysqlSnapshot(String param) {
        mySqlDataStore.doScheduledSave();
    }
}
