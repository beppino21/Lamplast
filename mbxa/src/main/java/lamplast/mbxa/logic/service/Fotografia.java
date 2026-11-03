package lamplast.mbxa.logic.service;

import java.math.BigDecimal;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lamplast.mbxa.logic.model.Ddt;
import lamplast.mbxa.logic.model.DdtPosizione;

/**
 * Fotografia (JSON) di tutti i dati che finiscono stampati sul DDT.
 * Serve a due cose: conservarla in ddt_versione e capire se, dall'ultima stampa,
 * è cambiato qualcosa (nuova versione) oppure no (ristampa identica).
 * Numeri e date sono salvati come stringhe, così il confronto non dipende da come
 * jsonb normalizza i valori.
 */
public final class Fotografia {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Fotografia() {}

    public static ObjectNode crea(Ddt d) {
        ObjectNode o = MAPPER.createObjectNode();
        put(o, "matDocAnno", d.getMatDocAnno());
        put(o, "matDoc", d.getMatDoc());
        put(o, "dataRegistrazione", d.getDataRegistrazione());
        put(o, "ddtTipoNum", d.getDdtTipoNum());
        put(o, "ddtAnno", d.getDdtAnno());
        put(o, "ddtNumero", d.getDdtNumero());
        put(o, "ddtData", d.getDdtData());
        put(o, "mittCodice", d.getMittCodice());
        put(o, "mittRagioneSociale", d.getMittRagioneSociale());
        put(o, "mittIndirizzo", d.getMittIndirizzo());
        put(o, "mittCap", d.getMittCap());
        put(o, "mittCitta", d.getMittCitta());
        put(o, "mittProvincia", d.getMittProvincia());
        put(o, "mittNazione", d.getMittNazione());
        put(o, "destCodice", d.getDestCodice());
        put(o, "destRagioneSociale", d.getDestRagioneSociale());
        put(o, "destIndirizzo", d.getDestIndirizzo());
        put(o, "destCap", d.getDestCap());
        put(o, "destCitta", d.getDestCitta());
        put(o, "destProvincia", d.getDestProvincia());
        put(o, "destNazione", d.getDestNazione());
        put(o, "destPartitaIva", d.getDestPartitaIva());
        // aggiunto dopo i primi DDT: solo se valorizzato, così le fotografie già salvate
        // (che non hanno la chiave) restano confrontabili e non nascono versioni inutili
        if (d.getDestCodiceFiscale() != null && !d.getDestCodiceFiscale().isBlank()) {
            put(o, "destCodiceFiscale", d.getDestCodiceFiscale());
        }
        put(o, "causale", d.getCausale());
        put(o, "porto", d.getPorto());
        put(o, "aspettoBeni", d.getAspettoBeni());
        put(o, "vettRagioneSociale", d.getVettRagioneSociale());
        put(o, "vettIndirizzo", d.getVettIndirizzo());
        put(o, "vettPartitaIva", d.getVettPartitaIva());
        put(o, "vettIscrizioneAlbo", d.getVettIscrizioneAlbo());
        put(o, "colli", d.getColli());
        put(o, "pesoLordo", d.getPesoLordo());
        put(o, "pesoNetto", d.getPesoNetto());
        put(o, "unitaPeso", d.getUnitaPeso());
        put(o, "inizioTrasporto", d.getInizioTrasporto());
        put(o, "annotazioni", d.getAnnotazioni());
        ArrayNode pos = o.putArray("posizioni");
        for (DdtPosizione p : d.getPosizioni()) {
            ObjectNode r = pos.addObject();
            put(r, "rigaSap", p.getRigaSap());
            put(r, "materiale", p.getMateriale());
            put(r, "descrizione", p.getDescrizione());
            put(r, "lotto", p.getLotto());
            put(r, "um", p.getUm());
            put(r, "quantita", p.getQuantita());
        }
        return o;
    }

    public static String toJson(ObjectNode o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Confronto indipendente dall'ordine delle chiavi (jsonb le riordina). */
    public static boolean uguali(ObjectNode attuale, String jsonSalvato) {
        if (jsonSalvato == null) return false;
        try {
            JsonNode salvato = MAPPER.readTree(jsonSalvato);
            return Objects.equals(attuale, salvato);
        } catch (Exception e) {
            return false;
        }
    }

    private static void put(ObjectNode o, String k, Object v) {
        if (v == null) {
            o.putNull(k);
        } else if (v instanceof BigDecimal) {
            o.put(k, ((BigDecimal) v).stripTrailingZeros().toPlainString());
        } else if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) o.putNull(k);
            else o.put(k, s);
        } else {
            o.put(k, v.toString());
        }
    }
}
