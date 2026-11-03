-- =====================================================================
--  MBXA - AZZERAMENTO DEI DDT  (pulizia tecnica, IRREVERSIBILE)
--
--  Cancella TUTTI i DDT con versioni, PDF ed eventi, e riparte la
--  numerazione interna. Le anagrafiche (stabilimenti, vettori, causali,
--  porti, aspetti) restano. Uso previsto: fine dei test prima dell'avvio
--  in produzione. Per un singolo DDT sbagliato si usa l'annullamento.
--
--  PRIMA: backup  ->  pg_dump -U postgres -Fc -f ddt_trasf_prima_azzeramento.dump ddt_trasf
--
--  Esecuzione (come proprietario delle tabelle, es. postgres), nella STESSA sessione:
--     psql -U postgres -d ddt_trasf
--     SET mbxa.conferma = 'AZZERA';
--     \i ddt_azzeramento.sql
--  In pgAdmin: stessa finestra Query Tool, prima la SET poi il contenuto di questo file.
--  Senza la SET lo script si ferma e non cancella nulla.
-- =====================================================================
BEGIN;
SET LOCAL search_path TO ddt;

DO $$
BEGIN
    IF current_setting('mbxa.conferma', true) IS DISTINCT FROM 'AZZERA' THEN
        RAISE EXCEPTION 'Azzeramento NON eseguito: manca la conferma (SET mbxa.conferma = ''AZZERA''; nella stessa sessione).';
    END IF;
END
$$;

-- Situazione prima dell'azzeramento
SELECT stato, count(*) AS ddt FROM ddt_testata GROUP BY stato ORDER BY stato;
SELECT (SELECT count(*) FROM ddt_versione) AS versioni_pdf,
       (SELECT count(*) FROM ddt_evento)   AS eventi;

-- TRUNCATE non attiva i trigger di riga che proteggono versioni e DDT emessi:
-- per questo è l'unico modo di svuotare le tabelle, e va usato solo qui.
TRUNCATE ddt_evento, ddt_versione, ddt_posizione, ddt_testata, ddt_numeratore RESTART IDENTITY;

SELECT count(*) AS ddt_rimasti FROM ddt_testata;

COMMIT;

-- la conferma vale una volta sola
RESET mbxa.conferma;
