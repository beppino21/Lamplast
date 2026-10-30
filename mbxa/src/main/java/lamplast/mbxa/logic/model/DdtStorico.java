package lamplast.mbxa.logic.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Righe "leggere" per elenchi e storico (nessun PDF in memoria). */
public final class DdtStorico {

    private DdtStorico() {}

    /** Riga dell'elenco DDT acquisiti. */
    public static class ElencoRiga implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id;
        public String ddtNumero;
        public LocalDate ddtData;
        public String matDoc;
        public String matDocAnno;
        public LocalDate dataRegistrazione;
        public String mittCodice;
        public String destCodice;
        public String destRagioneSociale;
        public String stato;
        public int versioneCorrente;
    }

    /** Versione stampata (senza il PDF). */
    public static class Versione implements Serializable {
        private static final long serialVersionUID = 1L;
        public int versione;
        public LocalDateTime creataAt;
        public String sha256;
    }

    /** Evento del registro. */
    public static class Evento implements Serializable {
        private static final long serialVersionUID = 1L;
        public LocalDateTime eventoAt;
        public String tipo;
        public Integer versione;
        public String postazione;
        public String note;
    }

    /** Filtro dell'elenco DDT. */
    public static class Filtro implements Serializable {
        private static final long serialVersionUID = 1L;
        public LocalDate dataDa;
        public LocalDate dataA;
        /** null = tutti. */
        public String stato;
        /** Ricerca libera su numero DDT, documento materiale, destinatario. */
        public String testo;
    }
}
