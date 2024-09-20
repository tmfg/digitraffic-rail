UPDATE train_ready
SET source = CASE source
                 WHEN '0' THEN 'PHONE'
                 WHEN '1' THEN 'LIIKE'
                 WHEN '2' THEN 'UNKNOWN'
                 WHEN '3' THEN 'KUPLA'
    END;

ALTER TABLE train_ready
    MODIFY COLUMN source ENUM ('PHONE', 'LIIKE', 'UNKNOWN', 'KUPLA');

ALTER TABLE train_ready
    MODIFY COLUMN accepted bit(1);


