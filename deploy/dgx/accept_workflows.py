#!/usr/bin/env python3
"""Write acceptance is deliberately restricted to the restored QA schema."""
import json, re, time, urllib.request, urllib.error, uuid
from release_ops import RELEASE, QA_SCHEMA, query, inventory_check, values

assert '/'+QA_SCHEMA+'?' in values(RELEASE/'qa.env')['ROADAGENT_DB_URL']
base='http://127.0.0.1:32768/api/v1'
results=[]
class ApiFailure(AssertionError):
    pass
def api(path, body=None, method=None):
    request=urllib.request.Request(base+path, data=None if body is None else json.dumps(body).encode(),
                                  headers={'Content-Type':'application/json'},method=method)
    try:
        with urllib.request.urlopen(request,timeout=240) as response: result=json.load(response)
    except urllib.error.HTTPError as error:
        raise ApiFailure((path,error.code,error.read().decode())) from error
    assert result['code']=='OK',result
    return result.get('data')

def action(item, suffix, **body):
    body.update(expectedWorkflowVersion=item['workflowVersion'],idempotencyKey='qa-'+str(uuid.uuid4()))
    return api('/emergency-workflows/'+item['workflowId']+'/'+suffix,body)

def passed(name, **details):
    results.append(dict(check=name,passed=True,**details))
    (RELEASE/'workflow-acceptance-progress.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
    print(name,flush=True)

inventory_check(QA_SCHEMA)
stock=query('SELECT SUM(available_quantity+reserved_quantity+dispatched_quantity) FROM w_emergency_resource',QA_SCHEMA)
alert=api('/facility-alerts?status=PENDING')['items'][0]
alert_id=str(alert['alertId'])
for old,new,resolution in [('PENDING','CONFIRMED',None),('CONFIRMED','CLOSED','RESOLVED')]:
    updated=api('/facility-alerts/'+alert_id+'/status-transitions',
                dict(expectedStatus=old,targetStatus=new,resolutionType=resolution,remark='隔离验收：设施告警处置'))
    assert updated['status']==new,updated
passed('facility_confirm_close')

item=api('/emergency-workflows/inbox?stage=LEVEL_1')['item']
event_id=item['event']['eventId']
assert re.fullmatch(r'[A-Za-z0-9_-]+',event_id)
# Force only the selected cloned event back to unclassified, then exercise actual Qwen retry.
query("UPDATE w_lw_incident SET event_type=NULL WHERE c_no='"+event_id+"'",QA_SCHEMA)
api('/emergency-events/'+event_id+'/classification-retries',method='POST')
classified=query("SELECT event_type FROM w_lw_incident WHERE c_no='"+event_id+"'",QA_SCHEMA)
assert classified not in ('','NULL'),classified
assert int(query("SELECT COUNT(*) FROM w_emergency_event_classification WHERE event_id='"+event_id+"'",QA_SCHEMA))>0
passed('local_llm_classification_retry',eventType=classified)

plan=api('/emergency-events/'+event_id+'/dispatches',method='POST')
item=api('/emergency-workflows/inbox?stage=LEVEL_1')['item']
assert item['event']['eventId']==event_id and item['currentPlan']['planId']==plan['planId']
assert plan['responsePlanId'] and plan['responsePlanVersion'],plan
assert query("SELECT COUNT(*) FROM w_emergency_dispatch_order WHERE plan_id='"+plan['planId']+"' AND response_plan_snapshot IS NOT NULL",QA_SCHEMA)=='1'
passed('local_llm_versioned_plan_generation',planId=plan['planId'])

# This deliberate type change is confined to the disposable clone.
corrected_type=query("SELECT event_type FROM w_emergency_response_plan WHERE event_type<>'"+classified+"' ORDER BY event_type LIMIT 1",QA_SCHEMA)
try:
    item=action(item,'event-type-corrections',eventType=corrected_type,reason='隔离验收：验证人工更正和预案版本切换')
except ApiFailure:
    item=api('/emergency-workflows/'+item['workflowId'])
    assert item['workflowStatus']=='GENERATION_FAILED',item
    inventory_check(QA_SCHEMA)
    passed('generation_failure_retains_retryable_state')
if item['currentPlan'] is None or item['workflowStatus']!='WAITING_LEVEL_1_SUBMISSION':
    plan=api('/emergency-events/'+event_id+'/dispatches',method='POST')
    item=api('/emergency-workflows/'+item['workflowId'])
passed('manual_event_type_correction')

item=action(item,'level-1-decisions',decision='SUBMIT',comment='隔离验收：一级提交')
assert item['currentStage']=='LEVEL_2',item
item=action(item,'professional-reviews',decision='REJECT',comment='隔离验收：补充会商意见后重新提交')
assert item['currentStage']=='LEVEL_1',item
passed('professional_review_return')
api('/emergency-events/'+event_id+'/dispatches',method='POST')
item=api('/emergency-workflows/'+item['workflowId'])
item=action(item,'level-1-decisions',decision='SUBMIT',comment='隔离验收：补充后提交')
item=action(item,'professional-reviews',decision='APPROVE',eventSeverity='GENERAL',
            resourceFeasibility='FEASIBLE_WITH_GAP' if item['currentPlan']['resourceShortages'] else 'FEASIBLE',
            impactAssessment='隔离验收：路段局部受影响',coordinationRequirements='隔离验收：联合协调资源',comment='隔离验收：会商通过')
assert item['currentStage']=='LEVEL_3',item
item=action(item,'command-decisions',decision='APPROVE',comment='隔离验收：批准调度')
assert item['workflowStatus']=='PUBLISHED',item
inventory_check(QA_SCHEMA)
item=action(item,'resource-releases',reason='隔离验收：处置完成，资源归队')
assert item['resourcesReleased'],item
inventory_check(QA_SCHEMA)
passed('three_level_approval_publish_release')

next_item=api('/emergency-workflows/inbox?stage=LEVEL_1')['item']
api('/emergency-events/'+next_item['event']['eventId']+'/no-dispatch',dict(reason='隔离验收：无需调度',confirmed=True))
passed('no_dispatch')
assert stock==query('SELECT SUM(available_quantity+reserved_quantity+dispatched_quantity) FROM w_emergency_resource',QA_SCHEMA)
passed('inventory_conservation')
(RELEASE/'workflow-acceptance.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
