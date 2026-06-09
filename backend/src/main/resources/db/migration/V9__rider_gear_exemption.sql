-- Add gear_exempt column to riders table (lives in oltp schema since V1)
ALTER TABLE oltp.riders
ADD COLUMN IF NOT EXISTS gear_exempt BOOLEAN DEFAULT FALSE NOT NULL;
