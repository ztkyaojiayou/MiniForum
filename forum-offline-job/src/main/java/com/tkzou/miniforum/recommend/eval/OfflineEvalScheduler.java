package com.tkzou.miniforum.recommend.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.prod.clickhouse.ClickHouseBehaviorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时离线评估
 * <p>
 * 让推荐系统"活"起来：周期性（默认 30 分钟）自动运行 {@link OfflineEvaluator}，
 * 基于当前全部行为日志做时间切分评估，把 7 项指标打到日志并追加到 data/eval-report.json
 * （累积可观察指标随数据积累的变化趋势）。
 * <p>
 * <b>数据流程</b>：BehaviorLogRepository（反馈信号）→ TimeSplitter 时间切分 → 训练集构建 ItemCF+热门
 * → 测试用户 TopK → Metrics 7 指标 → 日志 + eval-report.json 追加。
 * 数据量不足（行为数 < minBehaviors）时跳过，避免空评估噪音。
 */
@Component
public class OfflineEvalScheduler {

    private static final Logger log = LoggerFactory.getLogger(OfflineEvalScheduler.class);

    private final OfflineEvaluator evaluator;
    private final BehaviorLogRepository behaviorLogRepository;
    private final ObjectMapper objectMapper;
    /** 生产：从 ClickHouse 数仓读行为量；演示为 null → 内存仓库 */
    @Autowired(required = false)
    private ClickHouseBehaviorStore clickHouseBehaviorStore;

    @Value("${app.data-dir:./data}")
    private String dataDir;

    @Value("${app.rec.eval-enabled:true}")
    private boolean enabled;

    @Value("${app.rec.eval-train-ratio:0.8}")
    private double trainRatio;

    @Value("${app.rec.eval-top-k:20}")
    private int topK;

    @Value("${app.rec.eval-max-users:200}")
    private int maxUsers;

    @Value("${app.rec.eval-min-behaviors:50}")
    private long minBehaviors;

    public OfflineEvalScheduler(OfflineEvaluator evaluator,
                                BehaviorLogRepository behaviorLogRepository,
                                ObjectMapper objectMapper) {
        this.evaluator = evaluator;
        this.behaviorLogRepository = behaviorLogRepository;
        this.objectMapper = objectMapper;
    }

    /** 定时离线评估（默认每 30 分钟，可经 app.rec.eval-interval-ms 调整） */
    @Scheduled(fixedDelayString = "${app.rec.eval-interval-ms:1800000}")
    public void runEval() {
        if (!enabled) {
            return;
        }
        long behaviorCount = clickHouseBehaviorStore != null
                ? clickHouseBehaviorStore.count()      // 生产：数仓全量
                : behaviorLogRepository.count();        // 演示：内存
        if (behaviorCount < minBehaviors) {
            log.info("【离线评估】跳过：行为数据不足（{} 条 < {}）", behaviorCount, minBehaviors);
            return;
        }
        try {
            Metrics m = evaluator.evaluate(trainRatio, topK, maxUsers);
            String row = String.format(
                    "AUC=%.3f GAUC=%.3f Recall@%d=%.3f NDCG@%d=%.3f Coverage=%.3f Diversity=%.3f Freshness=%.3f（评估用户=%d, 行为=%d）",
                    m.getAuc(), m.getGauc(), m.getTopK(), m.getRecallAtK(),
                    m.getTopK(), m.getNdcgAtK(), m.getCoverage(), m.getDiversity(),
                    m.getFreshness(), m.getEvaluatedUsers(), behaviorCount);
            log.info("【离线评估】{}", row);
            appendReport(m, behaviorCount);
        } catch (Exception e) {
            log.warn("离线评估异常：{}", e.getMessage());
        }
    }

    /** 追加一条评估记录到 data/eval-report.json（累积趋势） */
    private void appendReport(Metrics m, long behaviorCount) {
        try {
            Path file = Paths.get(dataDir, "eval-report.json");
            List<Map<String, Object>> report = new ArrayList<>();
            if (Files.exists(file)) {
                report = objectMapper.readValue(file.toFile(),
                        new TypeReference<List<Map<String, Object>>>() {
                        });
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("time", LocalDateTime.now().toString());
            entry.put("behaviorCount", behaviorCount);
            entry.put("evaluatedUsers", m.getEvaluatedUsers());
            entry.put("topK", m.getTopK());
            entry.put("auc", round3(m.getAuc()));
            entry.put("gauc", round3(m.getGauc()));
            entry.put("recallAtK", round3(m.getRecallAtK()));
            entry.put("ndcgAtK", round3(m.getNdcgAtK()));
            entry.put("coverage", round3(m.getCoverage()));
            entry.put("diversity", round3(m.getDiversity()));
            entry.put("freshness", round3(m.getFreshness()));
            report.add(entry);
            Files.createDirectories(file.getParent());
            objectMapper.writeValue(file.toFile(), report);
        } catch (Exception e) {
            log.warn("写入离线评估报告失败：{}", e.getMessage());
        }
    }

    private double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
