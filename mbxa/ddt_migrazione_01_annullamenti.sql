-- =====================================================================
--  MBXA - migrazione 01: riemissione dopo annullamento
--  Da eseguire UNA volta su un database creato con la prima versione
--  di ddt_schema.sql (i database nuovi hanno già queste modifiche).
-- =====================================================================
SET search_path TO ddt;

BEGIN;

-- collegamento al DDT annullato che viene sostituito
ALTER TABLE ddt_testata ADD COLUMN sostituisce_id bigint REFERENCES ddt_testata(id);

-- un solo DDT NON annullato per documento materiale
ALTER TABLE ddt_testata DROP CONSTRAINT ddt_testata_un_matdoc;
CREATE UNIQUE INDEX ddt_testata_un_matdoc_attivo
    ON ddt_testata (mat_doc_anno, mat_doc)
    WHERE stato <> 'ANNULLATO';
CREATE INDEX ddt_testata_ix_matdoc ON ddt_testata (mat_doc_anno, mat_doc);

-- numerazione SAP: il numero di un DDT annullato torna libero; INTERNA: mai riusato
DROP INDEX ddt_testata_un_numero;
CREATE UNIQUE INDEX ddt_testata_un_numero
    ON ddt_testata (ddt_tipo_num, ddt_anno, ddt_numero)
    WHERE ddt_numero IS NOT NULL AND (stato <> 'ANNULLATO' OR ddt_tipo_num <> 'SAP');

COMMIT;
