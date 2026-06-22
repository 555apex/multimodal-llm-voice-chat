from flask import Flask
from flask_socketio import SocketIO
from flask_cors import CORS
from config import Config
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(name)s] %(levelname)s: %(message)s')

app = Flask(__name__)
app.config.from_object(Config)

CORS(app, resources={r"/*": {"origins": "*"}})

socketio = SocketIO(app, cors_allowed_origins="*", async_mode='threading')

from api.websocket import register_handlers
register_handlers(socketio)

@app.route('/health')
def health_check():
    return {'status': 'ok', 'message': '闽路通 v2.0 后端运行中'}

if __name__ == '__main__':
    logging.info('闽路通 v2.0 启动中...')
    logging.info(f'服务地址: http://localhost:{Config.PORT}')
    socketio.run(app, host='0.0.0.0', port=Config.PORT, debug=Config.DEBUG, use_reloader=False, allow_unsafe_werkzeug=True)
