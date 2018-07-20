delete from system_state_property where id = 'DATABASE_LOCKED_MODE';
insert into system_state_property (value, id) values ('false', 'DATABASE_LOCKED_MODE');

delete from system_state_property where id = 'DATABASE_LAZY_INIT_RUNNING';
insert into system_state_property (value, id) values ('false', 'DATABASE_LAZY_INIT_RUNNING');