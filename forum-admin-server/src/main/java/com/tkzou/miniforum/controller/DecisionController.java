package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.exception.BusinessException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机决策转盘接口（/api/decide，无需登录）
 * <p>
 * 提供选项集合 → 随机返回一个，并统计每个选项被选中的概率；纯内存实现，趣味功能。
 */
@RestController
@RequestMapping("/api/decide")
public class DecisionController {

    /** 最大允许的选项数量 */
    private static final int MAX_OPTIONS = 20;

    /** 选项请求体 */
    public static class DecideRequest {
        private List<String> options;

        public List<String> getOptions() {
            return options;
        }

        public void setOptions(List<String> options) {
            this.options = options;
        }
    }

    /** 决策结果响应体 */
    public static class DecideResponse {
        private final String result;
        private final List<Map<String, Object>> odds;

        public DecideResponse(String result, List<Map<String, Object>> odds) {
            this.result = result;
            this.odds = odds;
        }

        public String getResult() {
            return result;
        }

        public List<Map<String, Object>> getOdds() {
            return odds;
        }
    }

    /** 随机决策 */
    @PostMapping
    public Result<DecideResponse> decide(@RequestBody DecideRequest request) {
        List<String> options = request.getOptions();
        if (options == null || options.isEmpty()) {
            throw new BusinessException("请至少提供 1 个选项");
        }
        if (options.size() > MAX_OPTIONS) {
            throw new BusinessException("选项数量不能超过 " + MAX_OPTIONS + " 个");
        }

        // 去重并过滤空字符串
        List<String> cleaned = new ArrayList<>();
        for (String option : options) {
            String trimmed = option == null ? "" : option.trim();
            if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        if (cleaned.isEmpty()) {
            throw new BusinessException("选项不能为空");
        }

        // 随机选择
        String result = cleaned.get(ThreadLocalRandom.current().nextInt(cleaned.size()));

        // 计算每个选项的概率
        double chance = 100.0 / cleaned.size();
        List<Map<String, Object>> odds = new ArrayList<>();
        for (String option : cleaned) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("option", option);
            item.put("chance", Math.round(chance * 10.0) / 10.0);
            odds.add(item);
        }

        return Result.success(new DecideResponse(result, odds));
    }
}
