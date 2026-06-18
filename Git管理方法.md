# Git 管理方法

## 仓库信息

- **GitHub地址**: https://github.com/555apex/multimodal-llm-voice-chat.git
- **分支**: master
- **维护者**: 555apex

---

## 一、克隆项目到本地

### 方法1：命令行克隆到桌面（推荐）

**Windows:**
```bash
# 打开命令提示符或PowerShell，切换到桌面
cd %USERPROFILE%\Desktop

# 克隆项目
git clone https://github.com/555apex/multimodal-llm-voice-chat.git

# 进入项目
cd multimodal-llm-voice-chat
```

**Mac/Linux:**
```bash
# 打开终端，切换到桌面
cd ~/Desktop

# 克隆项目
git clone https://github.com/555apex/multimodal-llm-voice-chat.git

# 进入项目
cd multimodal-llm-voice-chat
```

### 方法2：使用GitHub Desktop

1. 下载安装 [GitHub Desktop](https://desktop.github.com/)
2. 登录你的GitHub账号
3. 点击 "Clone a repository"
4. 选择 `multimodal-llm-voice-chat`
5. 选择本地路径为桌面
6. 点击 "Clone"

---

## 二、环境配置

### 1. 安装Python依赖

```bash
# 进入版本2目录（推荐）
cd version_funasr_chattts/backend

# 创建虚拟环境（可选但推荐）
python -m venv venv
venv\Scripts\activate        # Windows
# source venv/bin/activate   # Mac/Linux

# 安装依赖
pip install -r requirements.txt
pip install edge-tts funasr modelscope ChatTTS
```

### 2. 安装Node.js依赖

```bash
# 进入前端目录
cd ../frontend

# 安装依赖
npm install
```

### 3. 配置环境变量

```bash
# 回到后端目录
cd ../backend

# 复制配置文件
cp .env.example .env

# 编辑 .env 文件，填入你的API Key
# 例如：DeepSeek API Key
LLM_API_KEY=your-deepseek-api-key-here
```

### 4. 启动服务

```bash
# 终端1：启动后端
cd version_funasr_chattts/backend
python app.py

# 终端2：启动前端
cd version_funasr_chattts/frontend
npm run dev
```

### 5. 访问应用

打开浏览器访问：**http://localhost:3000**

---

## 三、日常开发流程

### 1. 拉取最新代码

```bash
# 在项目根目录执行
git pull origin master
```

### 2. 修改代码后提交

```bash
# 查看修改状态
git status

# 添加修改的文件
git add .                    # 添加所有修改
git add filename.py          # 添加指定文件

# 提交修改
git commit -m "修改说明"

# 推送到GitHub
git push origin master
```

### 3. 查看提交历史

```bash
# 查看简洁历史
git log --oneline

# 查看详细历史
git log

# 查看某个文件的历史
git log --follow filename.py
```

---

## 四、使用Claude Code进行开发

### 1. 安装Claude Code CLI

```bash
npm install -g @anthropic-ai/claude-code
```

### 2. 在项目中启动Claude

```bash
# 进入项目目录
cd multimodal-llm-voice-chat

# 启动Claude Code
claude
```

### 3. 使用示例

```
> 帮我检查version_funasr_chattts的代码

> 修改config.py，添加新的配置项

> 更新开发日志

> 修复TTS合成问题
```

---

## 五、分支管理（多人协作）

### 1. 创建新分支

```bash
# 创建并切换到新分支
git checkout -b feature/新功能名称
```

### 2. 切换分支

```bash
# 切换到master分支
git checkout master

# 切换到指定分支
git checkout feature/新功能名称
```

### 3. 合并分支

```bash
# 切换到master分支
git checkout master

# 合并指定分支
git merge feature/新功能名称

# 推送合并结果
git push origin master
```

### 4. 删除分支

```bash
# 删除本地分支
git branch -d feature/新功能名称

# 删除远程分支
git push origin --delete feature/新功能名称
```

---

## 六、常见问题解决

### 1. 拉取时冲突

```bash
# 暂存本地修改
git stash

# 拉取远程代码
git pull origin master

# 恢复本地修改
git stash pop
```

### 2. 放弃本地修改

```bash
# 放弃指定文件的修改
git checkout -- filename.py

# 放弃所有修改
git checkout -- .
```

### 3. 查看远程仓库信息

```bash
git remote -v
```

### 4. 更新远程仓库地址

```bash
git remote set-url origin https://github.com/555apex/multimodal-llm-voice-chat.git
```

---

## 七、项目结构

```
multimodal-llm-voice-chat/
├── version_api/                    # 版本1: API调用版
├── version_funasr_chattts/        # 版本2: FunASR + ChatTTS
├── version_whisper_cosyvoice/     # 版本3: Whisper + CosyVoice
├── 版本说明.md                     # 项目概述
├── Git管理方法.md                  # 本文档
├── 代码审计报告.md                 # 审计报告
└── 代码修复总结.md                 # 修复记录
```

---

## 八、快速命令参考

| 操作 | 命令 |
|------|------|
| 克隆项目 | `git clone https://github.com/555apex/multimodal-llm-voice-chat.git` |
| 拉取更新 | `git pull origin master` |
| 查看状态 | `git status` |
| 添加文件 | `git add .` |
| 提交修改 | `git commit -m "说明"` |
| 推送更新 | `git push origin master` |
| 查看历史 | `git log --oneline` |
| 创建分支 | `git checkout -b feature/名称` |
| 切换分支 | `git checkout 分支名` |
| 合并分支 | `git merge 分支名` |

---

**最后更新**: 2026年6月18日
**维护者**: 555apex
