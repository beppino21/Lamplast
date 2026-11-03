package lamplast.mbxa.logic.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Testata DDT (tabella ddt_testata) con le sue posizioni.
 * Mittente, destinatario e vettore sono COPIE dei dati anagrafici al momento della
 * compilazione: una modifica successiva alle anagrafiche non altera i DDT già emessi.
 */
public class Ddt implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATO_BOZZA = "BOZZA";
    public static final String STATO_EMESSO = "EMESSO";
    public static final String STATO_ANNULLATO = "ANNULLATO";

    /** Tipo numerazione per il numero SAP inserito a mano (modo MANUALE). */
    public static final String TIPO_NUM_SAP = "SAP";

    private Long id;

    // documento materiale
    private String matDocAnno;
    private String matDoc;
    private LocalDate dataRegistrazione;
    private LocalDate dataDocumento;
    private String utenteSap;

    // numero DDT
    private String ddtTipoNum;
    private Integer ddtAnno;
    private Integer ddtProgressivo;
    private String ddtNumero;
    private LocalDate ddtData;

    // mittente
    private String mittCodice;
    private String mittRagioneSociale;
    private String mittIndirizzo;
    private String mittCap;
    private String mittCitta;
    private String mittProvincia;
    private String mittNazione;

    // destinatario
    private String destCodice;
    private String destRagioneSociale;
    private String destIndirizzo;
    private String destCap;
    private String destCitta;
    private String destProvincia;
    private String destNazione;
    private String destPartitaIva;
    private String destCodiceFiscale;

    // trasporto
    private String causale;
    private String porto;
    private String aspettoBeni;
    private Integer vettoreId;
    private String vettRagioneSociale;
    private String vettIndirizzo;
    private String vettPartitaIva;
    private String vettIscrizioneAlbo;
    private Integer colli;
    private BigDecimal pesoLordo;
    private BigDecimal pesoNetto;
    private String unitaPeso = "KG";
    private LocalDateTime inizioTrasporto;
    private String annotazioni;

    // stato
    private String stato = STATO_BOZZA;
    private int versioneCorrente;
    private String stornoMatDocAnno;
    private String stornoMatDoc;
    private LocalDateTime annullatoAt;
    private String motivoAnnullamento;
    /** DDT annullato che questo DDT sostituisce (stesso documento materiale). */
    private Long sostituisceId;

    private final ArrayList<DdtPosizione> posizioni = new ArrayList<>();

    // ------------------------------------------------------------------------
    // logica di stato
    // ------------------------------------------------------------------------

    public boolean isBozza() { return STATO_BOZZA.equals(stato); }
    public boolean isEmesso() { return STATO_EMESSO.equals(stato); }
    public boolean isAnnullato() { return STATO_ANNULLATO.equals(stato); }
    public boolean isStampato() { return versioneCorrente > 0; }

    /** Chiave leggibile del documento materiale, es. 4900425437/2026. */
    public String getMatDocChiave() { return matDoc + "/" + matDocAnno; }

    // ------------------------------------------------------------------------
    // getter / setter
    // ------------------------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getMatDocAnno() { return matDocAnno; }
    public void setMatDocAnno(String v) { matDocAnno = v; }
    public String getMatDoc() { return matDoc; }
    public void setMatDoc(String v) { matDoc = v; }
    public LocalDate getDataRegistrazione() { return dataRegistrazione; }
    public void setDataRegistrazione(LocalDate v) { dataRegistrazione = v; }
    public LocalDate getDataDocumento() { return dataDocumento; }
    public void setDataDocumento(LocalDate v) { dataDocumento = v; }
    public String getUtenteSap() { return utenteSap; }
    public void setUtenteSap(String v) { utenteSap = v; }
    public String getDdtTipoNum() { return ddtTipoNum; }
    public void setDdtTipoNum(String v) { ddtTipoNum = v; }
    public Integer getDdtAnno() { return ddtAnno; }
    public void setDdtAnno(Integer v) { ddtAnno = v; }
    public Integer getDdtProgressivo() { return ddtProgressivo; }
    public void setDdtProgressivo(Integer v) { ddtProgressivo = v; }
    public String getDdtNumero() { return ddtNumero; }
    public void setDdtNumero(String v) { ddtNumero = v; }
    public LocalDate getDdtData() { return ddtData; }
    public void setDdtData(LocalDate v) { ddtData = v; }
    public String getMittCodice() { return mittCodice; }
    public void setMittCodice(String v) { mittCodice = v; }
    public String getMittRagioneSociale() { return mittRagioneSociale; }
    public void setMittRagioneSociale(String v) { mittRagioneSociale = v; }
    public String getMittIndirizzo() { return mittIndirizzo; }
    public void setMittIndirizzo(String v) { mittIndirizzo = v; }
    public String getMittCap() { return mittCap; }
    public void setMittCap(String v) { mittCap = v; }
    public String getMittCitta() { return mittCitta; }
    public void setMittCitta(String v) { mittCitta = v; }
    public String getMittProvincia() { return mittProvincia; }
    public void setMittProvincia(String v) { mittProvincia = v; }
    public String getMittNazione() { return mittNazione; }
    public void setMittNazione(String v) { mittNazione = v; }
    public String getDestCodice() { return destCodice; }
    public void setDestCodice(String v) { destCodice = v; }
    public String getDestRagioneSociale() { return destRagioneSociale; }
    public void setDestRagioneSociale(String v) { destRagioneSociale = v; }
    public String getDestIndirizzo() { return destIndirizzo; }
    public void setDestIndirizzo(String v) { destIndirizzo = v; }
    public String getDestCap() { return destCap; }
    public void setDestCap(String v) { destCap = v; }
    public String getDestCitta() { return destCitta; }
    public void setDestCitta(String v) { destCitta = v; }
    public String getDestProvincia() { return destProvincia; }
    public void setDestProvincia(String v) { destProvincia = v; }
    public String getDestNazione() { return destNazione; }
    public void setDestNazione(String v) { destNazione = v; }
    public String getDestPartitaIva() { return destPartitaIva; }
    public void setDestPartitaIva(String v) { destPartitaIva = v; }
    public String getDestCodiceFiscale() { return destCodiceFiscale; }
    public void setDestCodiceFiscale(String v) { destCodiceFiscale = v; }
    public String getCausale() { return causale; }
    public void setCausale(String v) { causale = v; }
    public String getPorto() { return porto; }
    public void setPorto(String v) { porto = v; }
    public String getAspettoBeni() { return aspettoBeni; }
    public void setAspettoBeni(String v) { aspettoBeni = v; }
    public Integer getVettoreId() { return vettoreId; }
    public void setVettoreId(Integer v) { vettoreId = v; }
    public String getVettRagioneSociale() { return vettRagioneSociale; }
    public void setVettRagioneSociale(String v) { vettRagioneSociale = v; }
    public String getVettIndirizzo() { return vettIndirizzo; }
    public void setVettIndirizzo(String v) { vettIndirizzo = v; }
    public String getVettPartitaIva() { return vettPartitaIva; }
    public void setVettPartitaIva(String v) { vettPartitaIva = v; }
    public String getVettIscrizioneAlbo() { return vettIscrizioneAlbo; }
    public void setVettIscrizioneAlbo(String v) { vettIscrizioneAlbo = v; }
    public Integer getColli() { return colli; }
    public void setColli(Integer v) { colli = v; }
    public BigDecimal getPesoLordo() { return pesoLordo; }
    public void setPesoLordo(BigDecimal v) { pesoLordo = v; }
    public BigDecimal getPesoNetto() { return pesoNetto; }
    public void setPesoNetto(BigDecimal v) { pesoNetto = v; }
    public String getUnitaPeso() { return unitaPeso; }
    public void setUnitaPeso(String v) { unitaPeso = v; }
    public LocalDateTime getInizioTrasporto() { return inizioTrasporto; }
    public void setInizioTrasporto(LocalDateTime v) { inizioTrasporto = v; }
    public String getAnnotazioni() { return annotazioni; }
    public void setAnnotazioni(String v) { annotazioni = v; }
    public String getStato() { return stato; }
    public void setStato(String v) { stato = v; }
    public int getVersioneCorrente() { return versioneCorrente; }
    public void setVersioneCorrente(int v) { versioneCorrente = v; }
    public String getStornoMatDocAnno() { return stornoMatDocAnno; }
    public void setStornoMatDocAnno(String v) { stornoMatDocAnno = v; }
    public String getStornoMatDoc() { return stornoMatDoc; }
    public void setStornoMatDoc(String v) { stornoMatDoc = v; }
    public LocalDateTime getAnnullatoAt() { return annullatoAt; }
    public void setAnnullatoAt(LocalDateTime v) { annullatoAt = v; }
    public String getMotivoAnnullamento() { return motivoAnnullamento; }
    public void setMotivoAnnullamento(String v) { motivoAnnullamento = v; }
    public Long getSostituisceId() { return sostituisceId; }
    public void setSostituisceId(Long v) { sostituisceId = v; }
    public List<DdtPosizione> getPosizioni() { return posizioni; }
}
