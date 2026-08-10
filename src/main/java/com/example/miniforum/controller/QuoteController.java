package com.example.miniforum.controller;

import com.example.miniforum.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机灵感便签接口
 * <p>
 * 内置一句名言库，每次调用随机返回一条，附带作者与分类。
 * 纯内存实现，不依赖任何第三方中间件。
 */
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    /** 内置名言库 */
    private static final List<Quote> QUOTES = new ArrayList<>();

    static {
        QUOTES.add(new Quote("千里之行，始于足下。", "老子"));
        QUOTES.add(new Quote("不积跬步，无以至千里。", "荀子"));
        QUOTES.add(new Quote("业精于勤，荒于嬉；行成于思，毁于随。", "韩愈"));
        QUOTES.add(new Quote("纸上得来终觉浅，绝知此事要躬行。", "陆游"));
        QUOTES.add(new Quote("路漫漫其修远兮，吾将上下而求索。", "屈原"));
        QUOTES.add(new Quote("天行健，君子以自强不息。", "《周易》"));
        QUOTES.add(new Quote("博观而约取，厚积而薄发。", "苏轼"));
        QUOTES.add(new Quote("锲而不舍，金石可镂。", "荀子"));
        QUOTES.add(new Quote("学而不思则罔，思而不学则殆。", "孔子"));
        QUOTES.add(new Quote("穷则独善其身，达则兼济天下。", "孟子"));
        QUOTES.add(new Quote("长风破浪会有时，直挂云帆济沧海。", "李白"));
        QUOTES.add(new Quote("会当凌绝顶，一览众山小。", "杜甫"));
        QUOTES.add(new Quote("宝剑锋从磨砺出，梅花香自苦寒来。", "《警世贤文》"));
        QUOTES.add(new Quote("海纳百川，有容乃大；壁立千仞，无欲则刚。", "林则徐"));
        QUOTES.add(new Quote("问渠那得清如许？为有源头活水来。", "朱熹"));
        QUOTES.add(new Quote("勿以恶小而为之，勿以善小而不为。", "刘备"));
        QUOTES.add(new Quote("人生自古谁无死，留取丹心照汗青。", "文天祥"));
        QUOTES.add(new Quote("沉舟侧畔千帆过，病树前头万木春。", "刘禹锡"));
        QUOTES.add(new Quote("山重水复疑无路，柳暗花明又一村。", "陆游"));
        QUOTES.add(new Quote("不畏浮云遮望眼，自缘身在最高层。", "王安石"));
    }

    /** 随机返回一条名言 */
    @GetMapping("/random")
    public Result<Quote> random() {
        Quote quote = QUOTES.get(ThreadLocalRandom.current().nextInt(QUOTES.size()));
        return Result.success(quote);
    }

    /** 名言数量 */
    @GetMapping("/count")
    public Result<Integer> count() {
        return Result.success(QUOTES.size());
    }

    /** 名言数据载体 */
    public static class Quote {
        private final String text;
        private final String author;

        public Quote(String text, String author) {
            this.text = text;
            this.author = author;
        }

        public String getText() {
            return text;
        }

        public String getAuthor() {
            return author;
        }
    }
}
