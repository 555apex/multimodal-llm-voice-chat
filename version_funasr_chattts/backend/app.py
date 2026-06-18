from flask import Flask
from flask_socketio import SocketIO
from flask_cors import CORS
from config import Config

# 创建 Flask 应用
app = Flask(__name__)
app.config.from_object(Config)

# 启用 CORS
CORS(app, resources={r"/*": {"origins": "*"}})

# 初始化 SocketIO
socketio = SocketIO(app, cors_allowed_origins="*")

# 导入并注册 WebSocket 处理器
from api.websocket import register_handlers
register_handlers(socketio)

# 健康检查路由
@app.route('/health')
def health_check():
    return {'status': 'ok', 'message': '语音聊天系统后端运行中 (FunASR + ChatTTS)'}

if __name__ == '__main__':
    print("=" * 50)
    print("语音聊天系统后端 (本地部署版)")
    print("ASR: FunASR (Paraformer-zh)")
    print("TTS: ChatTTS")
    print("=" * 50)
    print(f"服务地址: http://localhost:{Config.PORT}")
    print("首次启动会自动下载模型，请耐心等待...")
    socketio.run(app, host='0.0.0.0', port=Config.PORT, debug=True, allow_unsafe_werkzeug=True)
