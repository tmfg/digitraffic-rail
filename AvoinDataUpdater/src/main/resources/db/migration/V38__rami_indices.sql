CREATE INDEX idx_rami_message_type ON rami_message (message_type);
CREATE INDEX idx_rami_message_train ON rami_message (train_number, train_departure_date);
CREATE INDEX idx_rami_message_station_short_code ON rami_message_station (station_short_code);