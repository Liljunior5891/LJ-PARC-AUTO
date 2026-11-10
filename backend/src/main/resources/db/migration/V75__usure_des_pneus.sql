-- Kilometres deja faits par un pneu lors de ses montages precedents :
-- remonte apres une depose, il garde son usure.
ALTER TABLE pneu ADD COLUMN kilometres_anterieurs BIGINT NOT NULL DEFAULT 0;
