INSERT INTO service_orders(tenant,order_id,status,total_paise)
SELECT 'demo','ORD-1001','DELAYED',250000
WHERE NOT EXISTS (SELECT 1 FROM service_orders WHERE tenant='demo' AND order_id='ORD-1001');
INSERT INTO service_orders(tenant,order_id,status,total_paise)
SELECT 'demo','ORD-1002','DELIVERED',175000
WHERE NOT EXISTS (SELECT 1 FROM service_orders WHERE tenant='demo' AND order_id='ORD-1002');
INSERT INTO service_orders(tenant,order_id,status,total_paise)
SELECT 'other-tenant','ORD-9001','DELAYED',500000
WHERE NOT EXISTS (SELECT 1 FROM service_orders WHERE tenant='other-tenant' AND order_id='ORD-9001');
