package lamplast.mbxa.logic.model;

import java.io.Serializable;

/** Anagrafiche accessorie: stabilimenti, vettori e tabelle di decodifica. */
public final class Anagrafiche {

    private Anagrafiche() {}

    /** Stabilimento (ana_stabilimento): codice = Plant SAP. */
    public static class Stabilimento implements Serializable {
        private static final long serialVersionUID = 1L;
        private String codice;
        private String ragioneSociale;
        private String indirizzo;
        private String cap;
        private String citta;
        private String provincia;
        private String nazione = "IT";
        private String partitaIva;
        private String codiceFiscale;
        private boolean attivo = true;

        public String getCodice() { return codice; }
        public void setCodice(String v) { codice = v; }
        public String getRagioneSociale() { return ragioneSociale; }
        public void setRagioneSociale(String v) { ragioneSociale = v; }
        public String getIndirizzo() { return indirizzo; }
        public void setIndirizzo(String v) { indirizzo = v; }
        public String getCap() { return cap; }
        public void setCap(String v) { cap = v; }
        public String getCitta() { return citta; }
        public void setCitta(String v) { citta = v; }
        public String getProvincia() { return provincia; }
        public void setProvincia(String v) { provincia = v; }
        public String getNazione() { return nazione; }
        public void setNazione(String v) { nazione = v; }
        public String getPartitaIva() { return partitaIva; }
        public void setPartitaIva(String v) { partitaIva = v; }
        public String getCodiceFiscale() { return codiceFiscale; }
        public void setCodiceFiscale(String v) { codiceFiscale = v; }
        public boolean isAttivo() { return attivo; }
        public void setAttivo(boolean v) { attivo = v; }
    }

    /** Vettore (ana_vettore). */
    public static class Vettore implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer id;
        private String ragioneSociale;
        private String indirizzo;
        private String cap;
        private String citta;
        private String provincia;
        private String nazione = "IT";
        private String partitaIva;
        private String iscrizioneAlbo;
        private boolean predefinito;
        private boolean attivo = true;

        /** Indirizzo su una riga, come viene copiato nel DDT. */
        public String getIndirizzoCompleto() {
            StringBuilder sb = new StringBuilder();
            append(sb, indirizzo, "");
            append(sb, cap, ", ");
            append(sb, citta, cap == null || cap.isBlank() ? ", " : " ");
            if (provincia != null && !provincia.isBlank()) sb.append(" (").append(provincia.trim()).append(")");
            return sb.toString();
        }

        private static void append(StringBuilder sb, String s, String sep) {
            if (s == null || s.isBlank()) return;
            if (sb.length() > 0) sb.append(sep);
            sb.append(s.trim());
        }

        public Integer getId() { return id; }
        public void setId(Integer v) { id = v; }
        public String getRagioneSociale() { return ragioneSociale; }
        public void setRagioneSociale(String v) { ragioneSociale = v; }
        public String getIndirizzo() { return indirizzo; }
        public void setIndirizzo(String v) { indirizzo = v; }
        public String getCap() { return cap; }
        public void setCap(String v) { cap = v; }
        public String getCitta() { return citta; }
        public void setCitta(String v) { citta = v; }
        public String getProvincia() { return provincia; }
        public void setProvincia(String v) { provincia = v; }
        public String getNazione() { return nazione; }
        public void setNazione(String v) { nazione = v; }
        public String getPartitaIva() { return partitaIva; }
        public void setPartitaIva(String v) { partitaIva = v; }
        public String getIscrizioneAlbo() { return iscrizioneAlbo; }
        public void setIscrizioneAlbo(String v) { iscrizioneAlbo = v; }
        public boolean isPredefinito() { return predefinito; }
        public void setPredefinito(boolean v) { predefinito = v; }
        public boolean isAttivo() { return attivo; }
        public void setAttivo(boolean v) { attivo = v; }
    }

    /** Tabelle di decodifica con la stessa struttura (codice, descrizione, predefinito, attivo). */
    public enum TipoDecodifica {
        CAUSALE("ana_causale", "Causali del trasporto"),
        PORTO("ana_porto", "Porto"),
        ASPETTO("ana_aspetto", "Aspetto esteriore dei beni");

        private final String m_table;
        private final String m_titolo;

        TipoDecodifica(String table, String titolo) {
            m_table = table;
            m_titolo = titolo;
        }

        /** Nome tabella: da enum, mai da input utente. */
        public String getTable() { return m_table; }
        public String getTitolo() { return m_titolo; }
    }

    /** Voce di una tabella di decodifica. */
    public static class Decodifica implements Serializable {
        private static final long serialVersionUID = 1L;
        private String codice;
        private String descrizione;
        private boolean predefinito;
        private boolean attivo = true;

        public String getCodice() { return codice; }
        public void setCodice(String v) { codice = v; }
        public String getDescrizione() { return descrizione; }
        public void setDescrizione(String v) { descrizione = v; }
        public boolean isPredefinito() { return predefinito; }
        public void setPredefinito(boolean v) { predefinito = v; }
        public boolean isAttivo() { return attivo; }
        public void setAttivo(boolean v) { attivo = v; }
    }
}
