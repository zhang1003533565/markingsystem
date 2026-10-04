package com.markingsystem.service;

import com.markingsystem.dto.DevelopmentResourceDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 发展助手 - 个性化资源推荐服务
 * 当前数据写死，后期可改为从数据库或外部数据源获取
 */
@Service
public class DevelopmentResourceService {

    /**
     * 获取架构工程师方向的学习资源推荐
     * 覆盖：理论、实战、分布式、云原生、性能调优、架构落地
     */
    public List<DevelopmentResourceDTO> listArchitectureResources() {
        return List.of(
                new DevelopmentResourceDTO(
                        1L,
                        "大型分布式系统架构设计实战手册",
                        "讲解微服务拆分原则、领域驱动 DDD 落地、业务边界划分，拆解高并发业务系统的架构演进思路，适合从开发转向架构的工程师夯实理论基础。",
                        "开源电子书，GitHub 搜索项目名即可在线阅读，支持 PDF 下载。"
                ),
                new DevelopmentResourceDTO(
                        2L,
                        "云原生 K8s 架构实战训练营",
                        "围绕 Kubernetes 生产环境，讲解容器编排、服务网格、资源调度、故障排查，学习云原生架构的设计思想与生产最佳实践。",
                        "B 站配套视频 + 开源实验文档，跟随文档可本地搭建实验环境动手实操。"
                ),
                new DevelopmentResourceDTO(
                        3L,
                        "高可用系统设计与故障演练指南",
                        "剖析限流、熔断、降级、多级缓存、容灾多活方案，学习如何设计可抗故障的稳定业务架构，包含大量线上真实故障复盘案例。",
                        "技术社区系列专栏，网页在线阅读，配套案例代码仓库可 fork 练习。"
                ),
                new DevelopmentResourceDTO(
                        4L,
                        "数据库与存储架构进阶",
                        "覆盖分库分表、读写分离、NoSQL 选型、事务与一致性问题，帮助架构师根据业务场景选择合适存储方案，规避存储层架构坑点。",
                        "技术书籍配套开放章节，可在线浏览，完整版本建议购买纸质 / 电子书籍。"
                ),
                new DevelopmentResourceDTO(
                        5L,
                        "系统架构评审方法论与案例集",
                        "讲解架构评审流程、风险点识别、技术选型权衡，汇集电商、后台管理、AI 服务等多场景架构评审实例，提升架构决策能力。",
                        "企业开源技术文档，访问项目知识库网页直接查阅。"
                ),
                new DevelopmentResourceDTO(
                        6L,
                        "大厂架构师技术成长实战课",
                        "从业务需求出发做架构推演，不堆砌技术名词，训练架构思维：需求拆解‑技术选型‑方案输出‑落地评估完整全流程。",
                        "视频课程，提供公开样例章节，完整内容需要平台内学习。"
                )
        );
    }
}
