# -*- coding: utf-8 -*-
"""
为 11 个固定分类补齐演示数据（每个分类都有帖子）：
  1) 用 admin 登录，读取 data/posts.json 并按映射 PUT 更新现有帖子的 category；
  2) 为数码/游戏/娱乐/体育/财经/教育/生活/其他 等分类新增测试帖（幂等：标题已存在则跳过）。
用法: python scripts/seed_categories.py   （需先启动服务 8090）
"""
import json
import http.client
import os

BASE_HOST = "localhost"
BASE_PORT = 8090

PASSWORDS = {
    "admin": "admin123",
    "aifan": "123456",
    "techgeek": "123456",
    "carboss": "123456",
    "newsdaily": "123456",
}

# 现有帖子 id -> 目标分类（按内容主题归类；id 1 为已删除验证帖，跳过）
EXISTING_CATEGORY = {
    2: "科技", 3: "科技", 4: "科技",
    5: "科技", 6: "数码", 7: "科技",
    8: "汽车", 9: "汽车", 10: "汽车",
    11: "时事", 12: "时事", 13: "时事",
    14: "其他",
}

# 新增测试帖：作者 -> [帖子...]（补齐空分类 + 丰富已有分类）
NEW_POSTS = {
    "techgeek": [
        {"title": "旗舰手机影像大战：一英寸大底是终点吗", "category": "数码",
         "tags": ["数码", "手机", "影像"],
         "content": "2026 年的旗舰机影像，一英寸大底已经成了标配，各家开始比拼算法和长焦。\n\n趋势观察：\n1. 计算摄影进一步进化，夜景和人像的质感差距拉大；\n2. 潜望长焦普及到 3-5 倍焦段，演唱会神器不再是旗舰专属；\n3. 视频能力成为新战场，4K 60 帧 + 电影色调成为卖点。\n\n对普通用户来说，手机影像早已够用，剩下的就是风格的取舍。"},
        {"title": "智能手表健康监测：从计步到救命", "category": "数码",
         "tags": ["数码", "手表", "健康"],
         "content": "智能手表早已不只是看通知和计步，健康监测正在变成核心卖点。\n\n实测体验：\n- 心率、血氧、睡眠监测越来越准，部分功能已接近医疗级；\n- 跌倒检测、心律失常提醒这类「救命功能」开始实用化；\n- 续航是最大痛点，一周一充仍是奢望。\n\n健康数据的价值在于长期趋势，而不是单次数值。"},
        {"title": "A 股半年报盘点：结构性行情下的机会", "category": "财经",
         "tags": ["财经", "股市", "半年报"],
         "content": "2026 年 A 股半年报收官，结构性行情特征明显。\n\n几个看点：\n1. 高股息资产持续受青睐，红利策略跑赢大盘；\n2. 科技成长板块分化，AI 算力与半导体业绩兑现；\n3. 消费板块弱复苏，白酒和家电出现分化。\n\n投资没有圣杯，看懂基本面、控制好仓位才是王道。"},
        {"title": "年轻人开始存钱了：居民储蓄率创新高", "category": "财经",
         "tags": ["财经", "储蓄", "消费"],
         "content": "最新数据显示，居民储蓄率持续走高，年轻人从「月光」转向「存钱」。\n\n原因分析：\n1. 经济不确定性下，防御性储蓄成为主流选择；\n2. 存款利率下行，「挪储」现象明显，理财和债基分流；\n3. 「省钱博主」走红，极简消费成为新的生活方式。\n\n会存钱是好事，但过度储蓄也值得警惕。"},
    ],
    "aifan": [
        {"title": "国产 3A 游戏出海：中国游戏的新名片", "category": "游戏",
         "tags": ["游戏", "3A", "出海"],
         "content": "国产 3A 游戏在国际市场屡创佳绩，成为文化输出的新名片。\n\n盘点：\n1. 多款国产大作登上 Steam 全球畅销榜前列；\n2. 中国神话、武侠题材在海外圈粉无数，文化认同感增强；\n3. 主机端与 PC 端齐头并进，买断制模式跑通。\n\n游戏出海不只是生意，更是中国叙事走向世界的一扇窗。"},
        {"title": "电竞入亚之后：电竞产业的下一站", "category": "游戏",
         "tags": ["游戏", "电竞", "产业"],
         "content": "电竞正式入亚后，产业进入规范化、大众化的新阶段。\n\n变化与机会：\n1. 俱乐部运营越来越专业，选手保障体系逐步完善；\n2. 电竞赛事与城市文旅结合，带动线下经济；\n3. AI 训练师、数据分析师等新职业涌现。\n\n电竞的黄金时代，才刚刚开始。"},
        {"title": "在线教育转型：AI 个性化学习成为新方向", "category": "教育",
         "tags": ["教育", "AI", "学习"],
         "content": "「双减」之后在线教育经历洗牌，AI 个性化学习成为新方向。\n\n现状观察：\n1. AI 讲题、自适应刷题成为头部产品标配；\n2. 素质教育赛道崛起，编程、艺术、体育培训需求旺盛；\n3. 教育硬件（学习机、词典笔）借 AI 焕发第二春。\n\n教育的内核没变，变的只是抵达知识的方式。"},
        {"title": "职业教育新风向：技能培训与就业直通", "category": "教育",
         "tags": ["教育", "职业", "就业"],
         "content": "就业市场结构性矛盾突出，职业教育成为缓解供需错配的关键。\n\n新趋势：\n1. 培训课程与企业岗位深度绑定，「入学即就业」模式兴起；\n2. AI、新能源、养老护理等新兴领域技能需求旺盛；\n3. 政府补贴力度加大，职业技能等级证书含金量提升。\n\n终身学习不是口号，而是这个时代的生存技能。"},
    ],
    "carboss": [
        {"title": "世界杯预选赛：国足冲击 2026 的关键一战", "category": "体育",
         "tags": ["体育", "足球", "国足"],
         "content": "2026 世界杯亚洲区预选赛进入白热化阶段，国足迎来关键战役。\n\n赛前分析：\n1. 归化球员与年轻新秀的磨合渐入佳境；\n2. 主场优势明显，球迷氛围将成第 12 人；\n3. 对手风格硬朗，中场硬度与防守反击是取胜关键。\n\n足球是圆的，一切皆有可能。为国足加油！"},
        {"title": "马拉松经济：全民跑步热带动千亿产业", "category": "体育",
         "tags": ["体育", "马拉松", "产业"],
         "content": "「马拉松热」持续升温，跑步经济规模突破千亿。\n\n数据说话：\n1. 全国马拉松赛事数量再创新高，报名中签率持续走低；\n2. 跑鞋、运动手表、补给品等装备消费增长迅猛；\n3. 赛事带动城市文旅，酒店、餐饮、旅游全面受益。\n\n跑步不仅是一种运动，更是一种生活方式。"},
    ],
    "newsdaily": [
        {"title": "暑期档电影票房破纪录：中国电影市场的春天", "category": "娱乐",
         "tags": ["娱乐", "电影", "暑期档"],
         "content": "2026 暑期档电影市场异常火爆，总票房刷新历史纪录。\n\n亮点盘点：\n1. 多部国产大片口碑票房双丰收，类型片百花齐放；\n2. 动画电影异军突起，成为暑期档最大黑马；\n3. IMAX、CINITY 等特效厅一票难求，观影体验升级。\n\n好电影永远有观众，市场用脚投票。"},
        {"title": "爆款综艺背后：综艺市场的新玩法", "category": "娱乐",
         "tags": ["娱乐", "综艺", "行业"],
         "content": "综艺市场进入存量竞争，但爆款依然频出。\n\n趋势观察：\n1. 「音乐 + 怀旧」组合拳屡试不爽，情怀经济被玩明白了；\n2. 户外真人秀与文旅结合，拍摄地带动当地旅游；\n3. 长短视频平台联动，综艺 IP 全产业链开发。\n\n内容行业永远在变，但打动人心永远是核心。"},
        {"title": "告别内耗：给生活做减法的 5 个方法", "category": "生活",
         "tags": ["生活", "极简", "心态"],
         "content": "信息爆炸的时代，我们比任何时候都需要给生活做减法。\n\n5 个实用方法：\n1. 每天留出 30 分钟「数字斋戒」，远离手机；\n2. 整理房间，断舍离不需要的物件；\n3. 减少无意义社交，把时间留给重要的人；\n4. 学会说「不」，拒绝精神内耗；\n5. 早睡早起，用规律打败焦虑。\n\n生活不是加法题，放下才能走得远。"},
        {"title": "城市漫步正流行：City Walk 的快乐哲学", "category": "生活",
         "tags": ["生活", "CityWalk", "城市"],
         "content": "City Walk（城市漫步）成为年轻人新的休闲方式。\n\n为什么流行：\n1. 不用做攻略、不用花钱，说走就走；\n2. 在熟悉的城市发现新鲜感，转角遇见惊喜；\n3. 与朋友边走边聊，是低成本的社交方式。\n\n城市的魅力，藏在每一条没走过的巷子里。"},
    ],
    "admin": [
        {"title": "关于社区运营规则的一些说明", "category": "其他",
         "tags": ["社区", "公告", "规则"],
         "content": "为了营造更好的社区氛围，补充几点运营规则说明：\n\n1. 请文明发言，理性讨论，禁止人身攻击；\n2. 广告与垃圾信息将被清理，恶意刷屏会被限制；\n3. 转载内容请注明出处，尊重原创；\n4. 遇到问题可通过页面留言反馈，管理员会尽快处理。\n\n感谢大家的支持，一起把社区建设得更好！"},
    ],
}


def login(username):
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT)
    conn.request("POST", "/api/auth/login",
                 body=json.dumps({"username": username, "password": PASSWORDS[username]}),
                 headers={"Content-Type": "application/json"})
    r = conn.getresponse()
    cookie = None
    sc = r.getheader("Set-Cookie")
    if sc:
        cookie = sc.split(";")[0]
    r.read()
    conn.close()
    if not cookie:
        raise RuntimeError(f"登录失败: {username}")
    return cookie


def call(method, path, body=None, cookie=None):
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT)
    headers = {"Content-Type": "application/json"}
    if cookie:
        headers["Cookie"] = cookie
    conn.request(method, path,
                 body=json.dumps(body) if body is not None else None,
                 headers=headers)
    r = conn.getresponse()
    txt = r.read().decode("utf-8")
    conn.close()
    return r.status, txt


def main():
    # 0) admin 登录
    admin_cookie = login("admin")
    print("[OK] admin 登录成功")

    # 1) 更新现有帖子的分类
    posts_path = os.path.join(os.path.dirname(__file__), "..", "data", "posts.json")
    with open(posts_path, encoding="utf-8") as f:
        posts = json.load(f)

    updated = 0
    for p in posts:
        pid = p.get("id")
        if pid not in EXISTING_CATEGORY:
            continue
        body = {
            "title": p["title"], "content": p["content"],
            "tags": p.get("tags") or [],
            "category": EXISTING_CATEGORY[pid],
            "publish": True,
        }
        status, txt = call("PUT", f"/api/posts/{pid}", body, admin_cookie)
        ok = "OK" if 200 <= status < 300 else "FAIL"
        print(f"[{ok}] 更新帖子 id={pid} -> 分类[{EXISTING_CATEGORY[pid]}] ({status})")
        if ok != "OK":
            print("      " + txt)
        else:
            updated += 1
    print(f"共更新 {updated} 篇现有帖子\n")

    # 2) 查询已有标题（幂等判断）
    existing_titles = set()
    status, txt = call("GET", "/api/posts?page=1&size=100", cookie=admin_cookie)
    if 200 <= status < 300:
        try:
            data = json.loads(txt).get("data") or {}
            for rec in data.get("records") or []:
                existing_titles.add(rec.get("title"))
        except Exception as e:
            print(f"[WARN] 解析已有帖子失败: {e}")
    print(f"当前已存在 {len(existing_titles)} 篇帖子标题")

    # 3) 新增分类测试帖
    created = 0
    for user, post_list in NEW_POSTS.items():
        cookie = login(user)
        for p in post_list:
            if p["title"] in existing_titles:
                print(f"[SKIP] {user}: {p['title']}")
                continue
            body = {"title": p["title"], "content": p["content"],
                    "tags": p["tags"], "category": p["category"], "publish": True}
            status, txt = call("POST", "/api/posts", body, cookie)
            ok = "OK" if 200 <= status < 300 else "FAIL"
            print(f"[{ok}] {user}: [{p['category']}] {p['title']} ({status})")
            if ok != "OK":
                print("      " + txt)
            else:
                created += 1
                existing_titles.add(p["title"])
    print(f"\n共新增 {created} 篇分类测试帖")
    print("完成！数据会由服务自动持久化到 data/*.json")


if __name__ == "__main__":
    main()
