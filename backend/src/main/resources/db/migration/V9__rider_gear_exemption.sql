-- Add gear_exempt column to riders table
ALTER TABLE enrollment.riders
ADD COLUMN gear_exempt BOOLEAN DEFAULT FALSE NOT NULL;
