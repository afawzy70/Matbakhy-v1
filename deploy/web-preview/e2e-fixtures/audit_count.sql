-- params: action
SELECT count(*) FROM audit_event WHERE action = :'action';
