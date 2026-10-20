"""Adapter for current workflow acceptance, restricted to the new local pilot DB."""
import json,os,subprocess
from pathlib import Path
if Path('/home/whtc/workspace/projects/road-agent-dgx').exists():raise SystemExit('Refuse write acceptance on source DGX')
if os.getenv('CONFIRM_LOCAL_WRITE_TEST')!='roadagent-fz-pilot':raise SystemExit('Set CONFIRM_LOCAL_WRITE_TEST=roadagent-fz-pilot')
ROOT=Path(__file__).resolve().parents[2]
ENV=Path(os.getenv('SITE_ENV','/opt/roadagent-migration/site.env')).resolve()
from local_guard import require_local
require_local(ENV)
RELEASE=Path(os.environ['SITE_EVIDENCE']).resolve();RELEASE.mkdir(parents=True,exist_ok=True)
QA_SCHEMA='road_agent'
dc=['docker','compose','--project-name','roadagent-fz-pilot','--env-file',str(ENV),'-f',str(ROOT/'resources/configs/compose.site.yaml')]
config=json.loads(subprocess.check_output(dc+['config','--format','json']))
url=config['services']['backend']['environment']['ROADAGENT_DB_URL']
if not url.startswith('jdbc:mysql://mysql:3306/road_agent?'):raise SystemExit('Backend DB is not the local pilot database')
if config['services']['mysql'].get('ports'):raise SystemExit('Unexpected exposed MySQL port')
def values(_):return {'ROADAGENT_DB_URL':url}
def query(sql,schema=QA_SCHEMA):
 if schema!=QA_SCHEMA:raise SystemExit('Only local pilot schema allowed')
 return subprocess.check_output(dc+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -uroadagent_site --batch --raw --skip-column-names road_agent -e "$1"','sh',sql],text=True).strip()
def inventory_check(schema):
 invalid=query('SELECT COUNT(*) FROM w_emergency_resource WHERE available_quantity<0 OR reserved_quantity<0 OR dispatched_quantity<0 OR available_quantity+reserved_quantity+dispatched_quantity>total_quantity',schema)
 assert invalid=='0','invalid inventory'
 mismatch=query('SELECT COUNT(*) FROM w_emergency_resource r LEFT JOIN (SELECT resource_id,SUM(IF(allocation_status=0,allocated_quantity,0)) AS reserved,SUM(IF(allocation_status=1,allocated_quantity,0)) AS dispatched FROM w_emergency_resource_allocation GROUP BY resource_id) a ON a.resource_id=r.resource_id WHERE r.reserved_quantity<>COALESCE(a.reserved,0) OR r.dispatched_quantity<>COALESCE(a.dispatched,0)',schema)
 assert mismatch=='0','inventory/allocation mismatch'
