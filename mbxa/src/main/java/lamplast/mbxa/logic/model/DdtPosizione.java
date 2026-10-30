package lamplast.mbxa.logic.model;

import java.io.Serializable;
import java.math.BigDecimal;

/** Posizione DDT (tabella ddt_posizione): una riga di uscita del 301. */
public class DdtPosizione implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String rigaSap;
    private int ordinamento;
    private String materiale;
    private String descrizione;
    private String lotto;
    private String um;
    private BigDecimal quantita;
    private String umBase;
    private BigDecimal quantitaBase;
    private BigDecimal pesoNetto;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getRigaSap() { return rigaSap; }
    public void setRigaSap(String v) { rigaSap = v; }
    public int getOrdinamento() { return ordinamento; }
    public void setOrdinamento(int v) { ordinamento = v; }
    public String getMateriale() { return materiale; }
    public void setMateriale(String v) { materiale = v; }
    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String v) { descrizione = v; }
    public String getLotto() { return lotto; }
    public void setLotto(String v) { lotto = v; }
    public String getUm() { return um; }
    public void setUm(String v) { um = v; }
    public BigDecimal getQuantita() { return quantita; }
    public void setQuantita(BigDecimal v) { quantita = v; }
    public String getUmBase() { return umBase; }
    public void setUmBase(String v) { umBase = v; }
    public BigDecimal getQuantitaBase() { return quantitaBase; }
    public void setQuantitaBase(BigDecimal v) { quantitaBase = v; }
    public BigDecimal getPesoNetto() { return pesoNetto; }
    public void setPesoNetto(BigDecimal v) { pesoNetto = v; }
}
