"""
环境检查脚本
检查版本2和版本3所需的依赖和环境
"""

import sys
import os
import importlib

def check_module(module_name, package_name=None):
    """检查模块是否可导入"""
    try:
        importlib.import_module(module_name)
        return True
    except ImportError:
        return False

def check_gpu():
    """检查GPU环境"""
    print("=" * 60)
    print("GPU 环境检查")
    print("=" * 60)

    # 检查 PyTorch
    if check_module('torch'):
        import torch
        print(f"✓ PyTorch: {torch.__version__}")
        print(f"  CUDA available: {torch.cuda.is_available()}")
        if torch.cuda.is_available():
            print(f"  CUDA version: {torch.version.cuda}")
            print(f"  GPU count: {torch.cuda.device_count()}")
            for i in range(torch.cuda.device_count()):
                print(f"  GPU {i}: {torch.cuda.get_device_name(i)}")
        else:
            print("  ⚠ CUDA 不可用，将使用 CPU（性能会显著降低）")
    else:
        print("✗ PyTorch 未安装")
        print("  安装命令: pip install torch torchvision torchaudio")

    # 检查 torchaudio
    if check_module('torchaudio'):
        import torchaudio
        print(f"✓ torchaudio: {torchaudio.__version__}")
    else:
        print("✗ torchaudio 未安装")

    print()

def check_version2_deps():
    """检查版本2依赖"""
    print("=" * 60)
    print("版本2 (FunASR + ChatTTS) 依赖检查")
    print("=" * 60)

    deps = {
        'funasr': 'FunASR',
        'ChatTTS': 'ChatTTS',
        'soundfile': 'soundfile',
        'numpy': 'numpy',
        'scipy': 'scipy',
    }

    all_ok = True
    for module, name in deps.items():
        if check_module(module):
            try:
                mod = importlib.import_module(module)
                version = getattr(mod, '__version__', 'installed')
                print(f"✓ {name}: {version}")
            except:
                print(f"✓ {name}: installed")
        else:
            print(f"✗ {name}: 未安装")
            all_ok = False

    print()
    return all_ok

def check_version3_deps():
    """检查版本3依赖"""
    print("=" * 60)
    print("版本3 (Whisper + CosyVoice) 依赖检查")
    print("=" * 60)

    deps = {
        'whisper': 'OpenAI Whisper',
        'soundfile': 'soundfile',
        'numpy': 'numpy',
        'scipy': 'scipy',
    }

    all_ok = True
    for module, name in deps.items():
        if check_module(module):
            try:
                mod = importlib.import_module(module)
                version = getattr(mod, '__version__', 'installed')
                print(f"✓ {name}: {version}")
            except:
                print(f"✓ {name}: installed")
        else:
            print(f"✗ {name}: 未安装")
            all_ok = False

    # CosyVoice 需要特殊检查
    if check_module('cosyvoice'):
        print("✓ CosyVoice: installed")
    else:
        print("✗ CosyVoice: 未安装 (需要从 GitHub 安装)")
        print("  安装命令: pip install git+https://github.com/FunAudioLLM/CosyVoice.git")
        all_ok = False

    print()
    return all_ok

def check_model_paths():
    """检查模型存储路径"""
    print("=" * 60)
    print("模型存储路径检查")
    print("=" * 60)

    paths = {
        r'D:\funasr_chattts': '版本2模型目录',
        r'D:\whisper_cosyvoice': '版本3模型目录',
    }

    for path, desc in paths.items():
        if os.path.exists(path):
            print(f"✓ {desc}: {path} (存在)")
            # 检查子目录
            for subdir in os.listdir(path):
                subdir_path = os.path.join(path, subdir)
                if os.path.isdir(subdir_path):
                    print(f"  └─ {subdir}/")
        else:
            print(f"✗ {desc}: {path} (不存在)")
            print(f"  将在首次运行时自动创建")

    print()

def check_disk_space():
    """检查磁盘空间"""
    print("=" * 60)
    print("磁盘空间检查")
    print("=" * 60)

    import shutil

    drives = ['C:', 'D:']
    for drive in drives:
        try:
            total, used, free = shutil.disk_usage(drive + '\\')
            print(f"{drive}: 总计 {total // (1024**3)} GB, "
                  f"已用 {used // (1024**3)} GB, "
                  f"可用 {free // (1024**3)} GB")

            if free < 10 * (1024**3):  # 小于 10GB
                print(f"  ⚠ 警告: 可用空间不足，建议至少 10GB")
        except:
            print(f"{drive}: 无法获取信息")

    print()

def provide_installation_guide():
    """提供安装指南"""
    print("=" * 60)
    print("安装指南")
    print("=" * 60)

    print("""
版本2 (FunASR + ChatTTS) 安装步骤:
----------------------------------
1. 安装 PyTorch (CUDA版本):
   pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118

2. 安装版本2依赖:
   cd version_funasr_chattts/backend
   pip install -r requirements.txt

3. 配置 API Key:
   cp .env.example .env
   # 编辑 .env 文件，填入 MIMO_API_KEY

4. 启动服务:
   python app.py

版本3 (Whisper + CosyVoice) 安装步骤:
--------------------------------------
1. 安装 PyTorch (CUDA版本):
   pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118

2. 安装 Whisper:
   pip install openai-whisper

3. 安装 CosyVoice:
   pip install git+https://github.com/FunAudioLLM/CosyVoice.git

4. 安装版本3依赖:
   cd version_whisper_cosyvoice/backend
   pip install -r requirements.txt

5. 配置 API Key:
   cp .env.example .env
   # 编辑 .env 文件，填入 MIMO_API_KEY

6. 启动服务:
   python app.py
""")

def main():
    """主函数"""
    print("\n" + "=" * 60)
    print("语音聊天系统 - 环境检查工具")
    print("=" * 60 + "\n")

    # 检查 GPU
    check_gpu()

    # 检查磁盘空间
    check_disk_space()

    # 检查模型路径
    check_model_paths()

    # 检查版本2依赖
    v2_ok = check_version2_deps()

    # 检查版本3依赖
    v3_ok = check_version3_deps()

    # 提供安装指南
    provide_installation_guide()

    # 总结
    print("=" * 60)
    print("检查结果总结")
    print("=" * 60)

    if v2_ok:
        print("✓ 版本2 依赖已满足")
    else:
        print("✗ 版本2 缺少依赖")

    if v3_ok:
        print("✓ 版本3 依赖已满足")
    else:
        print("✗ 版本3 缺少依赖")

    print()

if __name__ == '__main__':
    main()
