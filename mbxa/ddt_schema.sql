-- =====================================================================
--  DDT trasferimenti tra stabilimenti (mov. 301) - schema PostgreSQL
--  Fonte dati SAP: API_MATERIAL_DOCUMENT_SRV (S/4HANA Cloud PE)
--  Numero DDT: due modi, scelti da file di configurazione
--    MANUALE  = continuità con i DDT delle consegne: numero SAP (XABLN)
--               letto da MIGO e inserito dall'operatore
--    INTERNA  = serie propria dell'app (prefisso + progressivo per anno),
--               assegnata all'emissione tramite ddt_numeratore (senza buchi)
--  Unicità: tipo numerazione + anno + numero (in MANUALE solo tra i DDT non annullati)
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS ddt;
SET search_path TO ddt;

-- ---------------------------------------------------------------------
--  Funzione comune per updated_at
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
--  ANAGRAFICHE ACCESSORIE
-- =====================================================================

-- Stabilimenti (mittenti e destinatari): codice = Plant SAP
CREATE TABLE ana_stabilimento (
    codice           varchar(4)   PRIMARY KEY,
    ragione_sociale  varchar(120) NOT NULL,
    indirizzo        varchar(120) NOT NULL,
    cap              varchar(10)  NOT NULL,
    citta            varchar(60)  NOT NULL,
    provincia        varchar(2),
    nazione          varchar(2)   NOT NULL DEFAULT 'IT',
    partita_iva      varchar(20),
    attivo           boolean      NOT NULL DEFAULT true,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE TRIGGER ana_stabilimento_upd BEFORE UPDATE ON ana_stabilimento
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- Vettori
CREATE TABLE ana_vettore (
    id               serial       PRIMARY KEY,
    ragione_sociale  varchar(120) NOT NULL,
    indirizzo        varchar(120),
    cap              varchar(10),
    citta            varchar(60),
    provincia        varchar(2),
    nazione          varchar(2)   DEFAULT 'IT',
    partita_iva      varchar(20),
    iscrizione_albo  varchar(40),
    predefinito      boolean      NOT NULL DEFAULT false,
    attivo           boolean      NOT NULL DEFAULT true,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ana_vettore_un_predef ON ana_vettore (predefinito) WHERE predefinito;
CREATE TRIGGER ana_vettore_upd BEFORE UPDATE ON ana_vettore
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- Tabelle di decodifica (stessa struttura)
CREATE TABLE ana_causale (
    codice       varchar(10)  PRIMARY KEY,
    descrizione  varchar(80)  NOT NULL,
    predefinito  boolean      NOT NULL DEFAULT false,
    attivo       boolean      NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX ana_causale_un_predef ON ana_causale (predefinito) WHERE predefinito;

CREATE TABLE ana_porto (
    codice       varchar(10)  PRIMARY KEY,
    descrizione  varchar(80)  NOT NULL,
    predefinito  boolean      NOT NULL DEFAULT false,
    attivo       boolean      NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX ana_porto_un_predef ON ana_porto (predefinito) WHERE predefinito;

CREATE TABLE ana_aspetto (
    codice       varchar(10)  PRIMARY KEY,
    descrizione  varchar(80)  NOT NULL,
    predefinito  boolean      NOT NULL DEFAULT false,
    attivo       boolean      NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX ana_aspetto_un_predef ON ana_aspetto (predefinito) WHERE predefinito;

-- =====================================================================
--  DDT
-- =====================================================================

CREATE TABLE ddt_testata (
    id                   bigserial    PRIMARY KEY,

    -- Documento materiale SAP (un DDT per documento materiale)
    mat_doc_anno         char(4)      NOT NULL,
    mat_doc              varchar(10)  NOT NULL,
    data_registrazione   date         NOT NULL,          -- PostingDate
    data_documento       date,                           -- DocumentDate
    utente_sap           varchar(12),                    -- CreatedByUser

    -- Numero DDT, obbligatorio per emettere
    --   ddt_tipo_num   : 'SAP' in modo MANUALE, codice serie in modo INTERNA
    --   ddt_progressivo: valorizzato solo in modo INTERNA
    --   ddt_numero     : numero come stampato (es. '381' oppure 'TR2026/000015')
    ddt_tipo_num         varchar(10),
    ddt_anno             smallint,
    ddt_progressivo      integer      CHECK (ddt_progressivo IS NULL OR ddt_progressivo > 0),
    ddt_numero           varchar(20),
    ddt_data             date,

    -- Mittente (copia dall'anagrafica al momento della compilazione)
    mitt_codice          varchar(4)   NOT NULL,
    mitt_ragione_sociale varchar(120),
    mitt_indirizzo       varchar(120),
    mitt_cap             varchar(10),
    mitt_citta           varchar(60),
    mitt_provincia       varchar(2),
    mitt_nazione         varchar(2),

    -- Destinatario (copia dall'anagrafica)
    dest_codice          varchar(4)   NOT NULL,
    dest_ragione_sociale varchar(120),
    dest_indirizzo       varchar(120),
    dest_cap             varchar(10),
    dest_citta           varchar(60),
    dest_provincia       varchar(2),
    dest_nazione         varchar(2),
    dest_partita_iva     varchar(20),

    -- Dati di trasporto (completamento manuale)
    causale              varchar(80),
    porto                varchar(80),
    aspetto_beni         varchar(80),
    vettore_id           integer      REFERENCES ana_vettore(id),
    vett_ragione_sociale varchar(120),
    vett_indirizzo       varchar(200),
    vett_partita_iva     varchar(20),
    vett_iscrizione_albo varchar(40),
    colli                integer      CHECK (colli IS NULL OR colli >= 0),
    peso_lordo           numeric(13,3) CHECK (peso_lordo IS NULL OR peso_lordo >= 0),
    peso_netto           numeric(13,3) CHECK (peso_netto IS NULL OR peso_netto >= 0),
    unita_peso           varchar(3)   NOT NULL DEFAULT 'KG',
    inizio_trasporto     timestamp,
    annotazioni          text,

    -- Stato e versioni
    stato                varchar(10)  NOT NULL DEFAULT 'BOZZA'
                         CHECK (stato IN ('BOZZA','EMESSO','ANNULLATO')),
    versione_corrente    integer      NOT NULL DEFAULT 0,  -- 0 = mai stampato

    -- Annullamento (es. storno 302)
    storno_mat_doc_anno  char(4),
    storno_mat_doc       varchar(10),
    annullato_at         timestamptz,
    motivo_annullamento  varchar(200),
    -- DDT annullato che questo DDT sostituisce (stesso documento materiale)
    sostituisce_id       bigint       REFERENCES ddt_testata(id),

    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),

    -- un DDT emesso deve avere tipo numerazione, numero, anno e data
    CONSTRAINT ddt_testata_ck_numero CHECK (
        stato = 'BOZZA'
        OR (ddt_tipo_num IS NOT NULL AND ddt_numero IS NOT NULL
            AND ddt_anno IS NOT NULL AND ddt_data IS NOT NULL)
    ),
    -- in modo MANUALE (numero SAP) solo cifre e nessun progressivo interno
    CONSTRAINT ddt_testata_ck_sap CHECK (
        ddt_tipo_num IS DISTINCT FROM 'SAP'
        OR (ddt_numero ~ '^[0-9]+$' AND ddt_progressivo IS NULL)
    ),
    -- in modo INTERNA il progressivo è obbligatorio
    CONSTRAINT ddt_testata_ck_interna CHECK (
        ddt_tipo_num IS NULL OR ddt_tipo_num = 'SAP' OR ddt_progressivo IS NOT NULL
    )
);

-- Un solo DDT non annullato per documento materiale
-- (dopo un annullamento lo stesso 301 può avere un nuovo DDT)
CREATE UNIQUE INDEX ddt_testata_un_matdoc_attivo
    ON ddt_testata (mat_doc_anno, mat_doc)
    WHERE stato <> 'ANNULLATO';
CREATE INDEX ddt_testata_ix_matdoc ON ddt_testata (mat_doc_anno, mat_doc);

-- Numero DDT unico per tipo numerazione e anno.
-- Numerazione SAP (MANUALE): il numero di un DDT annullato torna libero, perché il
-- numero appartiene al documento materiale in SAP. Numerazione INTERNA: mai riusato.
CREATE UNIQUE INDEX ddt_testata_un_numero
    ON ddt_testata (ddt_tipo_num, ddt_anno, ddt_numero)
    WHERE ddt_numero IS NOT NULL AND (stato <> 'ANNULLATO' OR ddt_tipo_num <> 'SAP');
CREATE UNIQUE INDEX ddt_testata_un_progressivo
    ON ddt_testata (ddt_tipo_num, ddt_anno, ddt_progressivo)
    WHERE ddt_progressivo IS NOT NULL;

CREATE INDEX ddt_testata_ix_data  ON ddt_testata (ddt_data);
CREATE INDEX ddt_testata_ix_stato ON ddt_testata (stato);
CREATE INDEX ddt_testata_ix_dest  ON ddt_testata (dest_codice);

CREATE TRIGGER ddt_testata_upd BEFORE UPDATE ON ddt_testata
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- Numeratore per la numerazione INTERNA (non usato in modo MANUALE).
-- Assegnazione all'emissione, nella stessa transazione del passaggio a EMESSO:
--   INSERT INTO ddt_numeratore (tipo, anno) VALUES (:tipo, :anno)
--       ON CONFLICT DO NOTHING;
--   UPDATE ddt_numeratore SET ultimo_progressivo = ultimo_progressivo + 1,
--          updated_at = now()
--    WHERE tipo = :tipo AND anno = :anno
--   RETURNING ultimo_progressivo;
-- Il lock di riga dell'UPDATE serializza le emissioni: niente buchi né doppioni
-- (a differenza di una SEQUENCE, che in caso di rollback salta i numeri).
CREATE TABLE ddt_numeratore (
    tipo                varchar(10)  NOT NULL CHECK (tipo <> 'SAP'),
    anno                smallint     NOT NULL,
    ultimo_progressivo  integer      NOT NULL DEFAULT 0 CHECK (ultimo_progressivo >= 0),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (tipo, anno)
);

-- Posizioni: solo le righe di uscita del 301 (no riga automatica)
CREATE TABLE ddt_posizione (
    id            bigserial     PRIMARY KEY,
    testata_id    bigint        NOT NULL REFERENCES ddt_testata(id) ON DELETE CASCADE,
    riga_sap      varchar(4)    NOT NULL,          -- MaterialDocumentItem
    ordinamento   integer       NOT NULL,
    materiale     varchar(40)   NOT NULL,
    descrizione   varchar(120),
    lotto         varchar(10),
    um            varchar(3)    NOT NULL,          -- EntryUnit
    quantita      numeric(13,3) NOT NULL,          -- QuantityInEntryUnit
    um_base       varchar(3),
    quantita_base numeric(13,3),
    peso_netto    numeric(13,3),                   -- proposto da anagrafica prodotto
    CONSTRAINT ddt_posizione_un_riga UNIQUE (testata_id, riga_sap)
);

-- Versioni stampate: fotografia completa + PDF, la ristampa riusa il PDF
CREATE TABLE ddt_versione (
    id           bigserial    PRIMARY KEY,
    testata_id   bigint       NOT NULL REFERENCES ddt_testata(id),
    versione     integer      NOT NULL CHECK (versione > 0),
    creata_at    timestamptz  NOT NULL DEFAULT now(),
    dati         jsonb        NOT NULL,            -- testata + posizioni al momento della stampa
    pdf          bytea        NOT NULL,
    pdf_sha256   char(64)     NOT NULL,
    CONSTRAINT ddt_versione_un UNIQUE (testata_id, versione)
);

-- Registro eventi (senza login: postazione = host/IP del client)
CREATE TABLE ddt_evento (
    id           bigserial    PRIMARY KEY,
    testata_id   bigint       NOT NULL REFERENCES ddt_testata(id),
    versione     integer,
    tipo         varchar(12)  NOT NULL
                 CHECK (tipo IN ('ACQUISIZIONE','STAMPA','RISTAMPA','MODIFICA','ANNULLAMENTO')),
    evento_at    timestamptz  NOT NULL DEFAULT now(),
    postazione   varchar(100),
    note         varchar(500)
);
CREATE INDEX ddt_evento_ix_testata ON ddt_evento (testata_id, evento_at);

-- Le versioni non si modificano né si cancellano: tutela del documento emesso
CREATE OR REPLACE FUNCTION trg_versione_immutabile() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Le versioni stampate di un DDT non possono essere modificate o cancellate';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ddt_versione_no_upd BEFORE UPDATE OR DELETE ON ddt_versione
    FOR EACH ROW EXECUTE FUNCTION trg_versione_immutabile();

-- Un DDT emesso o annullato non si cancella
CREATE OR REPLACE FUNCTION trg_testata_no_delete() RETURNS trigger AS $$
BEGIN
    IF OLD.stato <> 'BOZZA' THEN
        RAISE EXCEPTION 'Il DDT % (anno %) è % e non può essere cancellato',
            OLD.ddt_numero, OLD.ddt_anno, OLD.stato;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ddt_testata_no_del BEFORE DELETE ON ddt_testata
    FOR EACH ROW EXECUTE FUNCTION trg_testata_no_delete();

-- =====================================================================
--  Dati iniziali (da rivedere con il cliente)
-- =====================================================================
INSERT INTO ana_causale (codice, descrizione, predefinito) VALUES
    ('TRASF', 'Trasferimento tra sedi', true);

INSERT INTO ana_aspetto (codice, descrizione, predefinito) VALUES
    ('VISTA', 'A vista', true);

INSERT INTO ana_porto (codice, descrizione, predefinito) VALUES
    ('FRANCO', 'Franco destino', true);
