/**
 * 离线作业运行器（forum-offline-job 入口）
 * <p>
 * {@link com.tkzou.miniforum.offline.OfflineJobApplication}：独立进程，装载共享域 + 推荐管线 + 离线评估，
 * 定时运行 OfflineEvalScheduler。无 web 控制器（纯离线批处理形态）。
 */
package com.tkzou.miniforum.offline;
