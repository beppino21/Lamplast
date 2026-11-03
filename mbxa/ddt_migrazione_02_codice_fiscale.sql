-- =====================================================================
--  MBXA - migrazione 02: codice fiscale separato dalla partita IVA
--  Da eseguire una volta sui database creati prima di questa versione
--  (i database nuovi, creati con ddt_schema.sql aggiornato, non ne hanno bisogno).
--  Non tocca i DDT esistenti: le nuove colonne nascono vuote.
--
--  psql -U postgres -d ddt_trasf -f ddt_migrazione_02_codice_fiscale.sql
-- =====================================================================
BEGIN;
SET search_path TO ddt;

ALTER TABLE ana_stabilimento ADD COLUMN IF NOT EXISTS codice_fiscale      varchar(20);
ALTER TABLE ddt_testata      ADD COLUMN IF NOT EXISTS dest_codice_fiscale varchar(20);

COMMIT;
