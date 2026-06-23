-- Demo catalog seed (idempotent). Run after migrations, against the persistent demo DB.
INSERT INTO oltp.inventory (item_id, store_id, name, price, stock, category, emoji, perishable, version, fragile, reserved_qty) VALUES
  ('MILK-001','store-test-1','Swiss Milk 1L',2.40,120,'Dairy','🥛',true,0,false,0),
  ('BREAD-001','store-test-1','Farmer Bread 500g',3.20,80,'Bakery','🍞',true,0,false,0),
  ('EGGS-001','store-test-1','Free-range Eggs x10',4.50,60,'Dairy','🥚',true,0,true,0),
  ('CHOC-001','store-test-1','Swiss Dark Chocolate',3.90,200,'Snacks','🍫',false,0,false,0),
  ('WATER-001','store-test-2','Alpine Still Water 6x1L',5.40,150,'Drinks','💧',false,0,false,0),
  ('COFFEE-001','store-test-2','Roasted Coffee Beans 1kg',18.90,40,'Drinks','☕',false,0,false,0),
  ('BANANA-001','store-test-2','Bananas 1kg',2.10,90,'Produce','🍌',true,0,false,0),
  ('CHEESE-001','store-test-2','Gruyère AOP 250g',6.80,55,'Dairy','🧀',true,0,true,0)
ON CONFLICT (item_id) DO NOTHING;
