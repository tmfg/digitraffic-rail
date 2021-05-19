delete
from category_code;
delete
from detailed_category_code;
delete
from third_category_code;

ALTER TABLE `category_code`
    CHANGE COLUMN `id` `oid` VARCHAR(128) NOT NULL;

ALTER TABLE `detailed_category_code`
    CHANGE COLUMN `id` `oid`                             VARCHAR(128) NOT NULL,
    CHANGE COLUMN `category_code_id` `category_code_oid` VARCHAR(128) NULL DEFAULT NULL;

ALTER TABLE `third_category_code`
    CHANGE COLUMN `id` `oid`                                               VARCHAR(128) NOT NULL,
    CHANGE COLUMN `detailed_category_code_id` `detailed_category_code_oid` VARCHAR(128) NOT NULL;

delete
from cause
where category_code_id is null
   or category_code_id = 61;

ALTER TABLE `cause`
    CHANGE COLUMN `category_code_id` `category_code_oid`                   VARCHAR(128) NOT NULL,
    CHANGE COLUMN `detailed_category_code_id` `detailed_category_code_oid` VARCHAR(128) NULL DEFAULT NULL,
    CHANGE COLUMN `third_category_code_id` `third_category_code_oid`       VARCHAR(128) NULL DEFAULT NULL;

update cause
set category_code_oid = '1.2.246.586.8.1.1'
where category_code_oid = '1';
update cause
set category_code_oid = '1.2.246.586.8.1.10'
where category_code_oid = '12';
update cause
set category_code_oid = '1.2.246.586.8.1.11'
where category_code_oid = '6';
update cause
set category_code_oid = '1.2.246.586.8.1.12'
where category_code_oid = '3';
update cause
set category_code_oid = '1.2.246.586.8.1.13'
where category_code_oid = '5';
update cause
set category_code_oid = '1.2.246.586.8.1.14'
where category_code_oid = '22';
update cause
set category_code_oid = '1.2.246.586.8.1.15'
where category_code_oid = '21';
update cause
set category_code_oid = '1.2.246.586.8.1.16'
where category_code_oid = '41';
update cause
set category_code_oid = '1.2.246.586.8.1.17'
where category_code_oid = '23';
update cause
set category_code_oid = '1.2.246.586.8.1.18'
where category_code_oid = '24';
update cause
set category_code_oid = '1.2.246.586.8.1.19'
where category_code_oid = '25';
update cause
set category_code_oid = '1.2.246.586.8.1.2'
where category_code_oid = '4';
update cause
set category_code_oid = '1.2.246.586.8.1.20'
where category_code_oid = '26';
update cause
set category_code_oid = '1.2.246.586.8.1.21'
where category_code_oid = '27';
update cause
set category_code_oid = '1.2.246.586.8.1.22'
where category_code_oid = '28';
update cause
set category_code_oid = '1.2.246.586.8.1.23'
where category_code_oid = '29';
update cause
set category_code_oid = '1.2.246.586.8.1.24'
where category_code_oid = '30';
update cause
set category_code_oid = '1.2.246.586.8.1.25'
where category_code_oid = '31';
update cause
set category_code_oid = '1.2.246.586.8.1.26'
where category_code_oid = '32';
update cause
set category_code_oid = '1.2.246.586.8.1.27'
where category_code_oid = '33';
update cause
set category_code_oid = '1.2.246.586.8.1.28'
where category_code_oid = '34';
update cause
set category_code_oid = '1.2.246.586.8.1.3'
where category_code_oid = '2';
update cause
set category_code_oid = '1.2.246.586.8.1.4'
where category_code_oid = '13';
update cause
set category_code_oid = '1.2.246.586.8.1.5'
where category_code_oid = '7';
update cause
set category_code_oid = '1.2.246.586.8.1.6'
where category_code_oid = '9';
update cause
set category_code_oid = '1.2.246.586.8.1.7'
where category_code_oid = '8';
update cause
set category_code_oid = '1.2.246.586.8.1.8'
where category_code_oid = '11';
update cause
set category_code_oid = '1.2.246.586.8.1.9'
where category_code_oid = '10';

update cause
set detailed_category_code_oid = '1.2.246.586.8.1.10.1'
where detailed_category_code_oid = '57';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.10.2'
where detailed_category_code_oid = '58';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.10.3'
where detailed_category_code_oid = '59';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.10.4'
where detailed_category_code_oid = '60';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.12.1'
where detailed_category_code_oid = '12';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.12.2'
where detailed_category_code_oid = '13';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.13.1'
where detailed_category_code_oid = '22';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.13.2'
where detailed_category_code_oid = '23';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.13.3'
where detailed_category_code_oid = '24';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.13.4'
where detailed_category_code_oid = '25';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.13.5'
where detailed_category_code_oid = '26';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.13.6'
where detailed_category_code_oid = '27';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.13.7'
where detailed_category_code_oid = '28';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.14.1'
where detailed_category_code_oid = '84';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.16.1'
where detailed_category_code_oid = '143';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.16.2'
where detailed_category_code_oid = '182';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.1'
where detailed_category_code_oid = '85';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.10'
where detailed_category_code_oid = '185';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.2'
where detailed_category_code_oid = '144';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.3'
where detailed_category_code_oid = '86';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.4'
where detailed_category_code_oid = '87';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.5'
where detailed_category_code_oid = '88';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.6'
where detailed_category_code_oid = '89';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.7'
where detailed_category_code_oid = '90';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.8'
where detailed_category_code_oid = '183';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.17.9'
where detailed_category_code_oid = '184';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.18.1'
where detailed_category_code_oid = '91';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.18.2'
where detailed_category_code_oid = '145';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.18.3'
where detailed_category_code_oid = '92';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.18.4'
where detailed_category_code_oid = '93';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.18.5'
where detailed_category_code_oid = '94';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.18.6'
where detailed_category_code_oid = '187';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.18.7'
where detailed_category_code_oid = '188';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.19.1'
where detailed_category_code_oid = '95';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.19.2'
where detailed_category_code_oid = '146';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.19.3'
where detailed_category_code_oid = '96';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.19.4'
where detailed_category_code_oid = '147';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.19.5'
where detailed_category_code_oid = '97';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.19.6'
where detailed_category_code_oid = '181';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.2.1'
where detailed_category_code_oid = '18';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.20.1'
where detailed_category_code_oid = '98';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.20.2'
where detailed_category_code_oid = '148';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.20.3'
where detailed_category_code_oid = '99';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.1'
where detailed_category_code_oid = '100';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.10'
where detailed_category_code_oid = '186';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.2'
where detailed_category_code_oid = '101';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.3'
where detailed_category_code_oid = '102';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.4'
where detailed_category_code_oid = '103';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.5'
where detailed_category_code_oid = '104';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.6'
where detailed_category_code_oid = '105';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.7'
where detailed_category_code_oid = '149';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.8'
where detailed_category_code_oid = '106';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.21.9'
where detailed_category_code_oid = '150';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.22.1'
where detailed_category_code_oid = '107';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.22.2'
where detailed_category_code_oid = '151';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.22.3'
where detailed_category_code_oid = '108';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.22.4'
where detailed_category_code_oid = '152';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.22.5'
where detailed_category_code_oid = '153';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.22.6'
where detailed_category_code_oid = '109';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.22.7'
where detailed_category_code_oid = '154';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.22.8'
where detailed_category_code_oid = '110';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.23.1'
where detailed_category_code_oid = '155';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.23.2'
where detailed_category_code_oid = '111';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.23.3'
where detailed_category_code_oid = '156';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.23.4'
where detailed_category_code_oid = '112';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.24.1'
where detailed_category_code_oid = '157';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.24.2'
where detailed_category_code_oid = '113';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.24.3'
where detailed_category_code_oid = '114';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.24.4'
where detailed_category_code_oid = '158';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.24.5'
where detailed_category_code_oid = '115';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.24.6'
where detailed_category_code_oid = '159';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.25.1'
where detailed_category_code_oid = '160';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.25.2'
where detailed_category_code_oid = '116';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.25.3'
where detailed_category_code_oid = '161';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.25.4'
where detailed_category_code_oid = '117';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.25.5'
where detailed_category_code_oid = '162';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.25.6'
where detailed_category_code_oid = '118';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.25.7'
where detailed_category_code_oid = '163';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.26.1'
where detailed_category_code_oid = '119';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.26.2'
where detailed_category_code_oid = '120';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.26.3'
where detailed_category_code_oid = '121';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.26.4'
where detailed_category_code_oid = '122';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.27.1'
where detailed_category_code_oid = '164';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.27.2'
where detailed_category_code_oid = '123';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.27.3'
where detailed_category_code_oid = '124';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.28.1'
where detailed_category_code_oid = '125';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.28.2'
where detailed_category_code_oid = '126';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.28.3'
where detailed_category_code_oid = '127';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.28.4'
where detailed_category_code_oid = '165';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.28.5'
where detailed_category_code_oid = '166';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.28.6'
where detailed_category_code_oid = '128';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.3.1'
where detailed_category_code_oid = '8';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.3.2'
where detailed_category_code_oid = '9';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.3.3'
where detailed_category_code_oid = '10';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.3.4'
where detailed_category_code_oid = '11';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.4.1'
where detailed_category_code_oid = '64';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.4.2'
where detailed_category_code_oid = '65';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.4.3'
where detailed_category_code_oid = '66';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.4.4'
where detailed_category_code_oid = '67';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.5.1'
where detailed_category_code_oid = '33';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.6.1'
where detailed_category_code_oid = '42';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.6.2'
where detailed_category_code_oid = '43';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.6.3'
where detailed_category_code_oid = '44';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.6.4'
where detailed_category_code_oid = '45';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.6.5'
where detailed_category_code_oid = '46';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.6.6'
where detailed_category_code_oid = '47';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.7.1'
where detailed_category_code_oid = '38';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.7.2'
where detailed_category_code_oid = '39';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.7.3'
where detailed_category_code_oid = '40';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.7.4'
where detailed_category_code_oid = '41';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.8.1'
where detailed_category_code_oid = '53';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.8.2'
where detailed_category_code_oid = '54';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.9.1'
where detailed_category_code_oid = '49';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.9.2'
where detailed_category_code_oid = '50';
update cause
set detailed_category_code_oid = '1.2.246.586.8.1.9.3'
where detailed_category_code_oid = '51';

update cause
set third_category_code_oid = '1.2.246.586.8.1.21.1.1'
where third_category_code_oid = '88';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.1.2'
where third_category_code_oid = '89';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.10.1'
where third_category_code_oid = '386';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.10.3'
where third_category_code_oid = '388';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.10.4'
where third_category_code_oid = '389';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.10.5'
where third_category_code_oid = '390';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.2.1'
where third_category_code_oid = '90';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.2.2'
where third_category_code_oid = '91';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.2.3'
where third_category_code_oid = '92';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.2.4'
where third_category_code_oid = '93';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.3.1'
where third_category_code_oid = '94';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.3.2'
where third_category_code_oid = '95';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.3.3'
where third_category_code_oid = '96';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.3.4'
where third_category_code_oid = '97';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.5.1'
where third_category_code_oid = '98';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.5.3'
where third_category_code_oid = '100';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.5.4'
where third_category_code_oid = '101';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.5.5'
where third_category_code_oid = '380';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.6.1'
where third_category_code_oid = '102';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.6.2'
where third_category_code_oid = '103';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.1'
where third_category_code_oid = '241';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.10'
where third_category_code_oid = '383';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.11'
where third_category_code_oid = '384';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.12'
where third_category_code_oid = '385';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.2'
where third_category_code_oid = '242';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.3'
where third_category_code_oid = '243';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.4'
where third_category_code_oid = '244';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.5'
where third_category_code_oid = '245';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.6'
where third_category_code_oid = '246';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.7'
where third_category_code_oid = '247';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.8'
where third_category_code_oid = '248';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.7.9'
where third_category_code_oid = '382';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.8.1'
where third_category_code_oid = '104';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.8.2'
where third_category_code_oid = '105';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.8.3'
where third_category_code_oid = '106';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.8.4'
where third_category_code_oid = '107';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.8.5'
where third_category_code_oid = '249';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.8.6'
where third_category_code_oid = '250';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.8.7'
where third_category_code_oid = '251';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.8.8'
where third_category_code_oid = '252';
update cause
set third_category_code_oid = '1.2.246.586.8.1.21.8.9'
where third_category_code_oid = '253';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.1'
where third_category_code_oid = '108';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.10'
where third_category_code_oid = '117';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.11'
where third_category_code_oid = '118';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.12'
where third_category_code_oid = '119';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.13'
where third_category_code_oid = '120';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.14'
where third_category_code_oid = '121';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.15'
where third_category_code_oid = '122';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.16'
where third_category_code_oid = '123';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.17'
where third_category_code_oid = '124';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.2'
where third_category_code_oid = '109';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.3'
where third_category_code_oid = '110';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.4'
where third_category_code_oid = '111';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.5'
where third_category_code_oid = '112';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.6'
where third_category_code_oid = '113';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.7'
where third_category_code_oid = '114';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.8'
where third_category_code_oid = '115';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.1.9'
where third_category_code_oid = '116';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.1'
where third_category_code_oid = '254';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.10'
where third_category_code_oid = '263';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.11'
where third_category_code_oid = '264';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.12'
where third_category_code_oid = '265';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.13'
where third_category_code_oid = '266';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.16'
where third_category_code_oid = '269';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.17'
where third_category_code_oid = '270';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.2'
where third_category_code_oid = '255';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.3'
where third_category_code_oid = '256';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.4'
where third_category_code_oid = '257';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.5'
where third_category_code_oid = '258';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.6'
where third_category_code_oid = '259';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.7'
where third_category_code_oid = '260';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.8'
where third_category_code_oid = '261';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.2.9'
where third_category_code_oid = '262';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.3.1'
where third_category_code_oid = '125';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.3.2'
where third_category_code_oid = '126';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.3.3'
where third_category_code_oid = '127';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.3.4'
where third_category_code_oid = '128';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.3.5'
where third_category_code_oid = '129';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.3.6'
where third_category_code_oid = '130';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.3.7'
where third_category_code_oid = '131';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.3.8'
where third_category_code_oid = '132';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.3.9'
where third_category_code_oid = '133';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.1'
where third_category_code_oid = '271';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.10'
where third_category_code_oid = '280';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.11'
where third_category_code_oid = '281';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.12'
where third_category_code_oid = '282';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.13'
where third_category_code_oid = '283';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.14'
where third_category_code_oid = '284';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.15'
where third_category_code_oid = '285';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.16'
where third_category_code_oid = '286';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.17'
where third_category_code_oid = '287';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.2'
where third_category_code_oid = '272';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.3'
where third_category_code_oid = '273';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.4'
where third_category_code_oid = '274';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.6'
where third_category_code_oid = '276';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.7'
where third_category_code_oid = '277';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.4.9'
where third_category_code_oid = '279';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.5.1'
where third_category_code_oid = '288';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.5.2'
where third_category_code_oid = '289';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.5.4'
where third_category_code_oid = '291';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.6.1'
where third_category_code_oid = '134';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.6.2'
where third_category_code_oid = '135';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.6.4'
where third_category_code_oid = '137';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.7.1'
where third_category_code_oid = '292';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.7.2'
where third_category_code_oid = '293';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.7.3'
where third_category_code_oid = '294';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.8.1'
where third_category_code_oid = '138';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.8.2'
where third_category_code_oid = '139';
update cause
set third_category_code_oid = '1.2.246.586.8.1.22.8.3'
where third_category_code_oid = '140';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.1.1'
where third_category_code_oid = '295';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.1.2'
where third_category_code_oid = '296';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.1.3'
where third_category_code_oid = '297';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.1.4'
where third_category_code_oid = '298';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.2.1'
where third_category_code_oid = '141';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.2.2'
where third_category_code_oid = '142';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.2.3'
where third_category_code_oid = '143';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.2.4'
where third_category_code_oid = '144';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.3.1'
where third_category_code_oid = '299';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.3.6'
where third_category_code_oid = '304';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.4.1'
where third_category_code_oid = '145';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.4.2'
where third_category_code_oid = '146';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.4.3'
where third_category_code_oid = '147';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.4.4'
where third_category_code_oid = '148';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.4.5'
where third_category_code_oid = '149';
update cause
set third_category_code_oid = '1.2.246.586.8.1.23.4.6'
where third_category_code_oid = '150';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.1.1'
where third_category_code_oid = '305';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.1.2'
where third_category_code_oid = '306';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.2.1'
where third_category_code_oid = '155';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.2.2'
where third_category_code_oid = '156';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.5.1'
where third_category_code_oid = '157';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.5.2'
where third_category_code_oid = '158';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.5.3'
where third_category_code_oid = '159';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.6.1'
where third_category_code_oid = '307';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.6.2'
where third_category_code_oid = '308';
update cause
set third_category_code_oid = '1.2.246.586.8.1.24.6.3'
where third_category_code_oid = '309';
update cause
set third_category_code_oid = '1.2.246.586.8.1.25.6.1'
where third_category_code_oid = '166';
update cause
set third_category_code_oid = '1.2.246.586.8.1.25.6.2'
where third_category_code_oid = '167';
update cause
set third_category_code_oid = '1.2.246.586.8.1.26.4.1'
where third_category_code_oid = '168';
update cause
set third_category_code_oid = '1.2.246.586.8.1.26.4.3'
where third_category_code_oid = '170';
update cause
set third_category_code_oid = '1.2.246.586.8.1.26.4.5'
where third_category_code_oid = '172';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.1.1'
where third_category_code_oid = '316';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.1.10'
where third_category_code_oid = '325';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.1.2'
where third_category_code_oid = '317';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.1.3'
where third_category_code_oid = '318';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.1.4'
where third_category_code_oid = '319';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.1.5'
where third_category_code_oid = '320';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.1.6'
where third_category_code_oid = '321';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.1.7'
where third_category_code_oid = '322';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.1.8'
where third_category_code_oid = '323';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.1'
where third_category_code_oid = '173';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.10'
where third_category_code_oid = '182';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.2'
where third_category_code_oid = '174';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.3'
where third_category_code_oid = '175';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.4'
where third_category_code_oid = '176';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.5'
where third_category_code_oid = '177';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.6'
where third_category_code_oid = '178';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.7'
where third_category_code_oid = '179';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.8'
where third_category_code_oid = '180';
update cause
set third_category_code_oid = '1.2.246.586.8.1.27.2.9'
where third_category_code_oid = '181';
update cause
set third_category_code_oid = '1.2.246.586.8.1.28.3.1'
where third_category_code_oid = '183';
update cause
set third_category_code_oid = '1.2.246.586.8.1.28.3.2'
where third_category_code_oid = '184';
update cause
set third_category_code_oid = '1.2.246.586.8.1.28.4.1'
where third_category_code_oid = '326';
update cause
set third_category_code_oid = '1.2.246.586.8.1.28.4.2'
where third_category_code_oid = '327';
update cause
set third_category_code_oid = '1.2.246.586.8.1.28.4.3'
where third_category_code_oid = '328';
