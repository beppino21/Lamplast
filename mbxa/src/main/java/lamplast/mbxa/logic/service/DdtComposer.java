package lamplast.mbxa.logic.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lamplast.mbxa.logic.model.Anagrafiche.Stabilimento;
import lamplast.mbxa.logic.model.Anagrafiche.Vettore;
import lamplast.mbxa.logic.model.Ddt;
import lamplast.mbxa.logic.model.DdtPosizione;
import lamplast.mbxa.logic.sap.ProductClient.ProductInfo;
import lamplast.mbxa.logic.sap.SapMaterialDocument;

/**
 * Compone la bozza di DDT a partire dal documento materiale SAP e dalle anagrafiche.
 * Nessun accesso a SAP o al database: tutto arriva come parametro (testabile).
 */
public final class DdtComposer {

    private DdtComposer() {}

    /** Valori proposti all'acquisizione. */
    public static class Default {
        public String causale;
        public String porto;
        public String aspetto;
        public Vettore vettore;
    }

    /** Bozza + avvisi per l'operatore. */
    public static class Risultato {
        public final Ddt ddt;
        public final List<String> avvisi = new ArrayList<>();

        Risultato(Ddt ddt) {
            this.ddt = ddt;
        }
    }

    /** Stabilimenti mittente e destinatario del 301 (verifica che siano uno solo ciascuno). */
    public static String[] stabilimenti(SapMaterialDocument doc) {
        List<SapMaterialDocument.Item> righe = doc.getOutgoingTransferItems();
        if (righe.isEmpty()) {
            throw new DdtException("Il documento " + doc.number + "/" + doc.year
                + " non contiene righe di uscita con movimento 301.");
        }
        Set<String> mitt = new LinkedHashSet<>();
        Set<String> dest = new LinkedHashSet<>();
        for (SapMaterialDocument.Item it : righe) {
            mitt.add(it.plant);
            dest.add(it.receivingPlant);
        }
        if (mitt.size() > 1) {
            throw new DdtException("Il documento ha più stabilimenti mittenti " + mitt + ": un DDT per documento non è possibile.");
        }
        if (dest.size() > 1) {
            throw new DdtException("Il documento ha più stabilimenti destinatari " + dest + ": un DDT per documento non è possibile.");
        }
        String m = mitt.iterator().next();
        String d = dest.iterator().next();
        if (d == null) throw new DdtException("Stabilimento destinatario non indicato nel documento " + doc.number + ".");
        if (d.equals(m)) {
            throw new DdtException("Mittente e destinatario coincidono (" + m + "): non è un trasferimento tra stabilimenti.");
        }
        return new String[] { m, d };
    }

    public static Risultato daDocumentoSap(SapMaterialDocument doc, Map<String, ProductInfo> prodotti,
            Stabilimento mittente, Stabilimento destinatario, Default def) {
        String[] plants = stabilimenti(doc);
        if (doc.isCancelled()) {
            throw new DdtException("Il documento " + doc.number + "/" + doc.year
                + " risulta stornato: non si emette il DDT.");
        }

        Ddt d = new Ddt();
        Risultato r = new Risultato(d);
        d.setMatDocAnno(doc.year);
        d.setMatDoc(doc.number);
        d.setDataRegistrazione(doc.postingDate);
        d.setDataDocumento(doc.documentDate);
        d.setUtenteSap(doc.createdByUser);
        d.setDdtData(doc.postingDate);
        d.setAnnotazioni(doc.headerText);

        d.setMittCodice(plants[0]);
        copiaMittente(d, mittente);
        if (mittente == null) {
            r.avvisi.add("Stabilimento mittente " + plants[0] + " non presente in anagrafica: completare prima di emettere.");
        }
        d.setDestCodice(plants[1]);
        copiaDestinatario(d, destinatario);
        if (destinatario == null) {
            r.avvisi.add("Stabilimento destinatario " + plants[1] + " non presente in anagrafica: completare prima di emettere.");
        }

        if (def != null) {
            d.setCausale(def.causale);
            d.setPorto(def.porto);
            d.setAspettoBeni(def.aspetto);
            copiaVettore(d, def.vettore);
        }

        BigDecimal totNetto = BigDecimal.ZERO;
        BigDecimal totLordo = BigDecimal.ZERO;
        boolean nettoNoto = false;
        boolean lordoNoto = false;
        List<String> senzaPeso = new ArrayList<>();
        int ord = 0;
        for (SapMaterialDocument.Item it : doc.getOutgoingTransferItems()) {
            ProductInfo pi = prodotti == null ? null : prodotti.get(it.material);
            DdtPosizione p = new DdtPosizione();
            p.setRigaSap(it.item);
            p.setOrdinamento(++ord);
            p.setMateriale(it.material);
            p.setDescrizione(pi == null ? null : pi.description);
            p.setLotto(it.batch);
            boolean entry = it.qtyEntry != null && it.entryUnit != null;
            p.setUm(entry ? it.entryUnit : it.baseUnit);
            p.setQuantita(entry ? it.qtyEntry : it.qtyBase);
            p.setUmBase(it.baseUnit);
            p.setQuantitaBase(it.qtyBase);

            BigDecimal netto = Pesi.rigaKg(it.qtyBase, it.baseUnit, pi == null ? null : pi.netWeight,
                pi == null ? null : pi.weightUnit);
            BigDecimal lordo = Pesi.rigaKg(it.qtyBase, it.baseUnit, pi == null ? null : pi.grossWeight,
                pi == null ? null : pi.weightUnit);
            p.setPesoNetto(netto);
            if (netto != null) {
                totNetto = totNetto.add(netto);
                nettoNoto = true;
            }
            BigDecimal lordoRiga = lordo != null ? lordo : netto;
            if (lordoRiga != null) {
                totLordo = totLordo.add(lordoRiga);
                lordoNoto = true;
            } else {
                senzaPeso.add(it.material);
            }
            d.getPosizioni().add(p);
        }
        d.setPesoNetto(nettoNoto ? totNetto : null);
        d.setPesoLordo(lordoNoto ? totLordo : null);
        if (!senzaPeso.isEmpty()) {
            r.avvisi.add("Peso non disponibile in anagrafica per " + senzaPeso + ": i pesi proposti sono incompleti, verificarli.");
        }
        return r;
    }

    public static void copiaMittente(Ddt d, Stabilimento s) {
        if (s == null) return;
        d.setMittRagioneSociale(s.getRagioneSociale());
        d.setMittIndirizzo(s.getIndirizzo());
        d.setMittCap(s.getCap());
        d.setMittCitta(s.getCitta());
        d.setMittProvincia(s.getProvincia());
        d.setMittNazione(s.getNazione());
    }

    public static void copiaDestinatario(Ddt d, Stabilimento s) {
        if (s == null) return;
        d.setDestRagioneSociale(s.getRagioneSociale());
        d.setDestIndirizzo(s.getIndirizzo());
        d.setDestCap(s.getCap());
        d.setDestCitta(s.getCitta());
        d.setDestProvincia(s.getProvincia());
        d.setDestNazione(s.getNazione());
        d.setDestPartitaIva(s.getPartitaIva());
    }

    /** Copia i dati del vettore; null = trasporto a cura del mittente. */
    public static void copiaVettore(Ddt d, Vettore v) {
        if (v == null) {
            d.setVettoreId(null);
            d.setVettRagioneSociale(null);
            d.setVettIndirizzo(null);
            d.setVettPartitaIva(null);
            d.setVettIscrizioneAlbo(null);
            return;
        }
        d.setVettoreId(v.getId());
        d.setVettRagioneSociale(v.getRagioneSociale());
        d.setVettIndirizzo(v.getIndirizzoCompleto());
        d.setVettPartitaIva(v.getPartitaIva());
        d.setVettIscrizioneAlbo(v.getIscrizioneAlbo());
    }

    /** Controlli prima dell'emissione: elenco dei problemi (vuoto = si può emettere). */
    public static List<String> problemiEmissione(Ddt d) {
        List<String> p = new ArrayList<>();
        if (vuoto(d.getMittRagioneSociale()) || vuoto(d.getMittIndirizzo()) || vuoto(d.getMittCitta())) {
            p.add("Dati del mittente incompleti (ragione sociale, indirizzo, città).");
        }
        if (vuoto(d.getDestRagioneSociale()) || vuoto(d.getDestIndirizzo()) || vuoto(d.getDestCitta())) {
            p.add("Dati del destinatario incompleti (ragione sociale, indirizzo, città).");
        }
        if (d.getDdtData() == null) p.add("Data del DDT mancante.");
        if (vuoto(d.getCausale())) p.add("Causale del trasporto mancante.");
        if (d.getPosizioni().isEmpty()) p.add("Il DDT non ha posizioni.");
        return p;
    }

    private static boolean vuoto(String s) {
        return s == null || s.isBlank();
    }
}
