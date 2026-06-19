from flask import Flask
from flask_socketio import SocketIO
from flask_cors import CORS
from config import Config

app = Flask(__name__)
app.config.from_object(Config)

CORS(app, resources={r"/*": {"origins": "*"}})

socketio = SocketIO(app, cors_allowed_origins="*", async_mode='threading')

from api.websocket import register_handlers
register_handlers(socketio)

@app.route('/health')
def health_check():
    return {'status': 'ok', 'message': '语音聊天系统后端运行中'}

if __name__ == '__main__':
    print(f"启动语音聊天系统后端...")
    print(f"服务地址: http://localhost:{Config.PORT}")
    socketio.run(app, host='0.0.0.0', port=Config.PORT, debug=Config.DEBUG, use_reloader=False, allow_unsafe_werkzeug=True)
