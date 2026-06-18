"""
代码结构验证脚本
验证版本2和版本3的代码结构、配置和语法是否正确
"""

import sys
import os
import ast
import json
from pathlib import Path

def check_python_syntax(file_path):
    """检查Python文件语法"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            source = f.read()
        ast.parse(source)
        return True, "语法正确"
    except SyntaxError as e:
        return False, f"语法错误: {e}"
    except Exception as e:
        return False, f"读取错误: {e}"

def check_config_file(config_path):
    """检查配置文件"""
    try:
        with open(config_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # 检查关键配置项
        checks = {
            'SECRET_KEY': 'secrets.token_hex' in content or 'os.getenv' in content,
            'DEBUG': "os.getenv('FLASK_DEBUG', 'False')" in content,
            'MIMO_API_KEY': 'MIMO_API_KEY' in content,
            'MODEL_CACHE_DIR': 'MODEL_CACHE_DIR' in content,
            'AUDIO_SAMPLE_RATE': 'AUDIO_SAMPLE_RATE' in content,
        }

        results = []
        for key, found in checks.items():
            if found:
                results.append(f"✓ {key}")
            else:
                results.append(f"✗ {key} 未找到")

        return True, results
    except Exception as e:
        return False, [f"读取失败: {e}"]

def verify_version(version_name, version_path):
    """验证一个版本的代码"""
    print("=" * 60)
    print(f"验证 {version_name}")
    print("=" * 60)

    issues = []
    successes = []

    # 检查目录结构
    required_dirs = ['backend', 'backend/api', 'backend/services', 'backend/utils']
    for dir_name in required_dirs:
        dir_path = os.path.join(version_path, dir_name)
        if os.path.exists(dir_path):
            successes.append(f"✓ 目录 {dir_name} 存在")
        else:
            issues.append(f"✗ 目录 {dir_name} 不存在")

    # 检查必要文件
    required_files = [
        'backend/app.py',
        'backend/config.py',
        'backend/api/websocket.py',
        'backend/services/asr_service.py',
        'backend/services/llm_service.py',
        'backend/services/tts_service.py',
        'backend/requirements.txt',
    ]

    for file_name in required_files:
        file_path = os.path.join(version_path, file_name)
        if os.path.exists(file_path):
            successes.append(f"✓ 文件 {file_name} 存在")

            # 检查 Python 语法
            if file_name.endswith('.py'):
                ok, msg = check_python_syntax(file_path)
                if ok:
                    successes.append(f"  └─ {msg}")
                else:
                    issues.append(f"✗ {file_name}: {msg}")
        else:
            issues.append(f"✗ 文件 {file_name} 不存在")

    # 检查配置文件
    config_path = os.path.join(version_path, 'backend/config.py')
    if os.path.exists(config_path):
        print("\n配置文件检查:")
        ok, results = check_config_file(config_path)
        if ok:
            for r in results:
                print(f"  {r}")
        else:
            print(f"  {results[0]}")

    # 检查 requirements.txt
    req_path = os.path.join(version_path, 'backend/requirements.txt')
    if os.path.exists(req_path):
        print("\n依赖检查:")
        with open(req_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
            for line in lines:
                line = line.strip()
                if line and not line.startswith('#'):
                    print(f"  - {line}")

    # 打印结果
    print("\n验证结果:")
    for s in successes:
        print(f"  {s}")
    for i in issues:
        print(f"  {i}")

    return len(issues) == 0

def compare_versions():
    """比较三个版本的代码"""
    print("\n" + "=" * 60)
    print("版本对比分析")
    print("=" * 60)

    base_path = Path("C:/Users/ruixuanhu/Desktop/基于两模态llm的语音聊天系统")

    versions = {
        'version_api': '版本1: API调用版',
        'version_funasr_chattts': '版本2: FunASR+ChatTTS',
        'version_whisper_cosyvoice': '版本3: Whisper+CosyVoice',
    }

    print("\n代码行数统计:")
    for version_dir, version_name in versions.items():
        version_path = base_path / version_dir
        if version_path.exists():
            total_lines = 0
            py_files = list(version_path.rglob('*.py'))
            for py_file in py_files:
                try:
                    with open(py_file, 'r', encoding='utf-8') as f:
                        total_lines += len(f.readlines())
                except:
                    pass
            print(f"  {version_name}: {len(py_files)} 个 Python 文件, {total_lines} 行代码")

    print("\n主要差异:")
    print("  版本1: 使用 mimo API 进行 ASR 和 TTS")
    print("  版本2: 使用本地 FunASR (中文最佳) 和 ChatTTS (对话自然)")
    print("  版本3: 使用本地 Whisper (多语言) 和 CosyVoice (高音质)")

def check_logging_improvements():
    """检查日志改进"""
    print("\n" + "=" * 60)
    print("代码改进验证")
    print("=" * 60)

    base_path = Path("C:/Users/ruixuanhu/Desktop/基于两模态llm的语音聊天系统")

    versions = ['version_api', 'version_funasr_chattts', 'version_whisper_cosyvoice']

    print("\n日志系统改进:")
    for version in versions:
        websocket_path = base_path / version / 'backend' / 'api' / 'websocket.py'
        if websocket_path.exists():
            with open(websocket_path, 'r', encoding='utf-8') as f:
                content = f.read()

            has_logging = 'import logging' in content
            has_logger = 'logger = logging.getLogger' in content
            has_deque = 'from collections import deque' in content

            status = "✓" if (has_logging and has_logger and has_deque) else "✗"
            print(f"  {status} {version}: logging={has_logging}, logger={has_logger}, deque={has_deque}")

def main():
    """主函数"""
    print("\n" + "=" * 60)
    print("语音聊天系统 - 代码结构验证")
    print("=" * 60)

    base_path = Path("C:/Users/ruixuanhu/Desktop/基于两模态llm的语音聊天系统")

    versions = [
        ('版本2 (FunASR + ChatTTS)', base_path / 'version_funasr_chattts'),
        ('版本3 (Whisper + CosyVoice)', base_path / 'version_whisper_cosyvoice'),
    ]

    all_ok = True
    for version_name, version_path in versions:
        if version_path.exists():
            ok = verify_version(version_name, version_path)
            if not ok:
                all_ok = False
        else:
            print(f"\n✗ {version_name} 目录不存在")
            all_ok = False

    # 比较版本
    compare_versions()

    # 检查改进
    check_logging_improvements()

    # 总结
    print("\n" + "=" * 60)
    print("验证总结")
    print("=" * 60)

    if all_ok:
        print("✓ 所有版本的代码结构验证通过")
        print("\n下一步:")
        print("  1. 安装所需依赖 (见 test_environment.py 的输出)")
        print("  2. 配置 .env 文件")
        print("  3. 启动服务进行测试")
    else:
        print("✗ 部分版本存在问题，请检查上述错误")

if __name__ == '__main__':
    main()
