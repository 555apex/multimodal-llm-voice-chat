# RoadAgent 当前同步与访问架构

更新日期：2026-09-14。

## 业务代码同步与发布

```mermaid
flowchart TD
    A["GitHub 上游业务分支<br/>version/roadagent-v1"] -->|拉取锁定提交| B["本地上游仓库<br/>multimodal-llm-voice-chat"]
    B -->|业务差异审计与语义合并| C["本地合并工作区<br/>road-agent-merged"]
    D["DGX 保留能力<br/>数字人、Qwen3.6、ASR、TTS、部署配置"] --> C
    C --> E["业务后端、DGX 前端和本地模型适配"]
    E --> F["构建、自动测试与隔离数据库验证"]
    F -->|通过后同步| G["DGX 主项目<br/>/home/whtc/workspace/projects/road-agent-dgx"]
    F -->|提交最终工程| H["GitHub roadagent-v2"]
    G --> I["同一组前后端镜像"]
    I --> J["内网运行实例"]
    I --> K["公网运行实例"]
```

可编辑 Mermaid 源文件：`docs/diagrams/current-sync-flow.mmd`。

PNG 文件：`docs/diagrams/current-sync-flow.png`。

## 当前公网与内网访问

```mermaid
flowchart LR
    A["公网用户"] -->|HTTPS :16194| B["www-api-db.u4065293.nyat.app"]
    B --> C["SakuraFrp 公网节点"]
    C --> D["DGX frpc"]
    D --> E["127.0.0.1:18081"]
    E --> F["公网前端 / Basic Auth"]
    F -->|/api/v1| G["公网后端"]
    H["DGX / Tailnet 用户"] -->|HTTP :18080| I["内网前端"]
    I -->|/api/v1| J["内网后端"]
    G --> R["共享后端依赖"]
    J --> R
    R --> K["共享业务数据库"]
    R --> L["本地 Qwen3.6、ASR、TTS"]
```

当前公网主界面：<https://www-api-db.u4065293.nyat.app:16194/>。

当前公网数字人演示：<https://www-api-db.u4065293.nyat.app:16194/digital-human-demo.html>。

可编辑 Mermaid 源文件：`docs/diagrams/current-public-access-flow.mmd`。

PNG 文件：`docs/diagrams/current-public-access-flow.png`。
