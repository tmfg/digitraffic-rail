alter table train_ready
 add  column  departure_date DATE NOT NULL,
 add  column  train_number BIGINT(20) NOT NULL,
 add  column  attap_id BIGINT(20) NOT NULL;
alter table train_ready add CONSTRAINT time_table_row_cause_FK
    foreign key (departure_date ,train_number , attap_id)
    references time_table_row (departure_date , train_number , attap_id)
    on delete cascade;

alter table time_table_row drop foreign key `train_ready_FK`;
alter table time_table_row drop index train_ready_FK_idx;
alter table time_table_row drop column train_ready_id;