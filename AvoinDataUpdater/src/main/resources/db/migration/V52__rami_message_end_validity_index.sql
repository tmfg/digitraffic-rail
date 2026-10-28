-- SIRI-SX reads only messages that have not expired yet (or expired very recently), so it can skip most of the table.
CREATE INDEX rami_message_end_validity ON rami_message (end_validity);
