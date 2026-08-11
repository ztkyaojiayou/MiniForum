# -*- coding: utf-8 -*-
"""
初始化演示帖子：登录各用户并发布 AI/科技/汽车/时事 主题帖子
用法: python seed_posts.py
"""
import json
import http.client

BASE_HOST = "localhost"
BASE_PORT = 8090

# 用户名 -> 密码（admin 密码与其他演示用户不同）
USERS_PASSWORD = {
    "admin": "admin123",
    "aifan": "123456",
    "techgeek": "123456",
    "carboss": "123456",
    "newsdaily": "123456",
}


def login(username):
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT)
    conn.request("POST", "/api/auth/login",
                 body=json.dumps({"username": username, "password": USERS_PASSWORD[username]}),
                 headers={"Content-Type": "application/json"})
    r = conn.getresponse()
    cookie = None
    sc = r.getheader("Set-Cookie")
    if sc:
        cookie = sc.split(";")[0]
    r.read()
    conn.close()
    return cookie


def call(method, path, body=None, cookie=None):
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT)
    headers = {"Content-Type": "application/json"}
    if cookie:
        headers["Cookie"] = cookie
    conn.request(method, path, body=json.dumps(body) if body is not None else None, headers=headers)
    r = conn.getresponse()
    txt = r.read().decode("utf-8")
    conn.close()
    return r.status, txt


def create_posts(username, posts):
    cookie = login(username)
    results = []
    for p in posts:
        status, txt = call("POST", "/api/posts", p, cookie)
        results.append((p.get("title"), status, txt))
    return results


POSTS = {
    "aifan": [
        {"title": "大模型落地元年：从聊天机器人到智能体",
         "content": "2026 年是大模型从「对话玩具」走向「生产工具」的关键一年。智能体（Agent）不再只是概念，而是开始真正接管工作流——从自动写周报到帮运营调投放。\n\n几个值得关注的方向：\n1. 多模态能力成为标配，图文音视频一体的助手体验大幅提升；\n2. 推理成本持续下降，中小企业也能用得起高质量模型；\n3. 「模型即服务」向「智能体即服务」演进，AI 从回答问题变成完成任务。\n\n未来的竞争，不再是参数之争，而是谁能把模型和业务场景深度绑定。",
         "tags": ["AI", "大模型", "智能体"],
         "publish": True},
        {"title": "多模态大模型：让 AI 真正「看懂」世界",
         "content": "从纯文本到图文理解，多模态大模型补齐了 AI 感知世界的关键拼图。\n\n实测印象：\n- 图片问答：能准确描述图表趋势、识别场景细节，还能定位图片中的具体区域；\n- 文档理解：长文档、表格、截图混排的内容也能稳定提取关键信息；\n- 视频理解：虽然还处于早期，但已经能对短视频内容做摘要。\n\n多模态不只是「加了个摄像头」，它让 AI 第一次能像人一样把视觉信息和语言逻辑统一起来。这也是未来具身智能、自动驾驶的基础能力。",
         "tags": ["AI", "多模态", "技术"],
         "publish": True},
        {"title": "开源大模型这一年：从追赶者到主力军",
         "content": "过去一年，开源大模型生态发生了质变。\n\n几个标志性事件：\n1. 多款开源模型在推理、代码、数学等榜单上追平甚至反超闭源旗舰；\n2. 开源社区的微调生态越来越繁荣，LoRA、量化方案让个人开发者也能玩转百亿参数模型；\n3. 国内开源模型出海，在全球开发者社区获得大量关注。\n\n开源的意义不只是免费，而是让 AI 能力从少数巨头手中释放出来，形成百花齐放的创新生态。对开发者来说，现在是最好的入场时机。",
         "tags": ["AI", "开源", "大模型"],
         "publish": True},
    ],
    "techgeek": [
        {"title": "国产芯片这一年：从追赶走向实用",
         "content": "芯片国产化进程在 2026 年进入深水区。\n\n进展与观察：\n1. 先进制程稳步推进，成熟制程产能持续扩大，国产芯片在消费电子、工业控制领域渗透率明显提升；\n2. 存储芯片价格回暖，头部厂商开始盈利，行业进入良性循环；\n3. EDA 工具链和光刻等上游环节仍是短板，但已有局部突破。\n\n芯片是长周期产业，比的不是一朝一夕，而是持续投入的决心。守住基本盘、攻克关键点，是未来几年的主线。",
         "tags": ["科技", "芯片", "半导体"],
         "publish": True},
        {"title": "折叠屏手机：从尝鲜品到主力机",
         "content": "折叠屏手机终于在 2026 年完成了从「极客玩具」到「大众主力机」的转变。\n\n体验分享：\n- 铰链工艺大幅进步，折痕几乎不可见，开合手感更扎实；\n- 轻薄化是最大突破，部分机型重量已经接近直板旗舰；\n- 价格下探到 5000 元档，双十一销量同比翻倍。\n\n大屏带来的分屏效率、阅读体验是真实需求，而不是伪需求。加上 AI 大模型在折叠屏上有了新的交互玩法（折叠态唤起 AI 助手），这代产品确实能打。",
         "tags": ["科技", "手机", "折叠屏"],
         "publish": True},
        {"title": "操作系统国产化：生态建设是关键",
         "content": "国产操作系统正在从「能用」走向「好用」，但生态仍是最大瓶颈。\n\n现状梳理：\n1. 桌面端：办公、浏览器、影音等常用软件适配率大幅提升；\n2. 开发者工具链逐步补齐，主流 IDE 和框架开始原生支持；\n3. 政企信创市场渗透率稳定增长，但消费级市场接受度仍需培育。\n\n生态建设没有捷径，需要时间沉淀。好消息是，随着 AI 应用爆发，新的应用形态给了国产 OS 弯道超车的窗口——谁先跑通「OS+AI」的体验，谁就占得先机。",
         "tags": ["科技", "操作系统", "信创"],
         "publish": True},
    ],
    "carboss": [
        {"title": "新能源车渗透率再创新高：燃油车的黄昏",
         "content": "最新数据显示，新能源车渗透率已突破 55%，历史性超越燃油车成为市场主流。\n\n几个关键信号：\n1. 插混与增程贡献了主要增量，「可油可电」解决了补能焦虑；\n2. 10-20 万主流价格带竞争白热化，价格战从新能源蔓延到燃油车；\n3. 智能化成为新车标配，没有高阶智驾的车型越来越难卖。\n\n对消费者而言，现在是换新能源车的好时机——技术成熟、价格实惠、选择丰富。燃油车的时代正在落幕，但它的余晖还能持续很多年。",
         "tags": ["汽车", "新能源", "行业"],
         "publish": True},
        {"title": "智能驾驶「端到端」时代：开过就回不去了",
         "content": "端到端大模型上车，让智能驾驶进入了新的阶段。\n\n真实体验：\n- 城市领航（NOA）不再是摆设，上下班通勤基本可以全程托管；\n- 决策更「像人」，变道果断、跟车平顺，遇到加塞也能从容应对；\n- 泊车能力大幅提升，窄车位、断头路都能一把入库。\n\n当然也要客观看待：极端天气、复杂施工路段仍是难点，L3 责任界定也还在探索。但趋势已经明确——智能驾驶正在从「辅助」走向「替代」。",
         "tags": ["汽车", "智驾", "端到端"],
         "publish": True},
        {"title": "汽车价格战的下半场：卷价格还是卷价值",
         "content": "持续两年的汽车价格战，进入了下半场。\n\n观察：\n1. 单纯降价已经失灵，消费者更看重「性价比+体验」的综合价值；\n2. 部分车企开始收缩战线、聚焦盈利，亏损换市场的模式难以为继；\n3. 「卷」的焦点从配置堆料转向智能化、服务与补能体系。\n\n对行业来说，价格战加速了优胜劣汰，头部集中度提升。对消费者来说，买到的是更便宜、更好的产品。但健康的行业需要合理利润，期待明年能看到更多「价值竞争」而非「价格绞杀」。",
         "tags": ["汽车", "价格战", "行业"],
         "publish": True},
    ],
    "newsdaily": [
        {"title": "本周科技圈大事盘点（8月第二周）",
         "content": "本周科技圈看点不少，挑几件值得关注的：\n\n1. AI 大模型：多家厂商发布新版本，推理成本再降三成，「价格战」从手机卷到了模型；\n2. 新能源：某头部车企发布新一代纯电平台，续航里程突破 1000 公里；\n3. 半导体：又有两项关键技术实现国产替代突破；\n4. 政策面：多地出台算力基础设施支持政策，智算中心建设提速。\n\n科技行业永远不缺新闻，缺的是把新闻变成趋势的判断力。下周见。",
         "tags": ["时事", "科技", "周报"],
         "publish": True},
        {"title": "算力新基建：从概念到落地的这一年",
         "content": "「东数西算」工程推进三年多，算力基础设施建设进入收获期。\n\n数据与趋势：\n1. 全国智算中心数量快速增长，总算力规模居全球前列；\n2. 国产 AI 芯片在智算中心的装机比例显著提升；\n3. 绿电+液冷成为新建数据中心标配，算力能耗持续优化。\n\n算力是数字经济的底座。当 AI 从云端走向终端、从训练走向推理，算力的分布形态还会继续演变。谁能把算力用得更高效，谁就能在 AI 时代占据主动。",
         "tags": ["时事", "算力", "基建"],
         "publish": True},
        {"title": "AI 监管新动向：发展与安全如何平衡",
         "content": "全球范围内，AI 治理正从「讨论」走向「立法」。\n\n最新进展：\n1. 国内《人工智能法》草案进入审议阶段，明确分级分类监管思路；\n2. 欧盟《人工智能法案》全面落地，高风险场景监管细则逐步明确；\n3. 深度合成内容标识成为共识，「AI 生成内容需打标」在多国落地。\n\n监管不是要「管死」AI，而是为技术创新划定清晰的边界。对产业而言，合规能力正在成为新的竞争力。理解规则、拥抱规则，才能走得更远。",
         "tags": ["时事", "AI", "监管"],
         "publish": True},
    ],
    "admin": [
        {"title": "欢迎来到 MiniForum 迷你微博论坛",
         "content": "欢迎各位！这里是 MiniForum 迷你微博论坛系统。\n\n当前版本已支持：\n- 发帖 / 浏览动态（支持分页、搜索、标签筛选）\n- 点赞、评论互动\n- 个人主页与我的文章管理\n- 草稿箱（存草稿、后发布）\n- 数据持久化（JSON 落盘，重启不丢）\n\n已初始化了 AI、科技、汽车、时事等主题的演示帖子，大家可以在广场浏览、互动。\n\n欢迎多提建议，一起把这个小社区建设得更好！",
         "tags": ["社区", "公告", "欢迎"],
         "publish": True},
    ],
}

total = 0
for user, posts in POSTS.items():
    results = create_posts(user, posts)
    for title, status, txt in results:
        ok = "OK" if 200 <= status < 300 else "FAIL"
        print(f"[{ok}] {user}: {title} -> {status}")
        if ok != "OK":
            print("      " + txt)
        total += 1

print(f"\n共发布 {total} 篇帖子")
