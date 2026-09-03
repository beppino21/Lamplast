package eOne.conditionsSD.s4client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legge le associazioni imballo cliente/materiale dalla vista pronta
 * ImballoClienteMat (CDS ZI_ZIMBAL_3), che espone già risolte — via
 * associazione lato CDS — le descrizioni IT/EN di entrambe le parti
 * dell'imballo (ZIMBAL_1 + ZIMBAL_2): una sola chiamata basta sia per la
 * stampa sia per l'indicatore in griglia.
 *
 * Cascata di risoluzione, per ciascuna coppia cliente/materiale:
 *   1) match specifico  (cliente = X, materiale = Y)
 *   2) match generico   (cliente vuoto, materiale = Y) — imballo di default per quel materiale
 *   3) nessuno trovato  → mancante
 *
 * IMPORTANTE — OData V4: la busta di risposta è {"value": [...]}, diversa da
 * quella V2 {"d": {"results": [...]}} usata dalle API standard SAP.
 *
 * NOTA: il path del servizio è quello comunicato da Beppe (in attesa di
 * conferma definitiva da chi lo gestisce) — da aggiornare se cambia.
 */
public class Imbal3ReadClient {

    private static final String ENTITY_PATH =
        "/sap/opu/odata4/sap/zsb_zimbal_3/srvd/sap/zsd_zimbal_3/0001/ImballoClienteMat";

    private static final int BATCH_SIZE = 20;

    public enum Specificity { SPECIFIC, GENERIC, MISSING }

    /**
     * Separatore usato per incapsulare, nello stesso testo restituito da
     * {@link Imbal3Info#getText}, sia la riga "Imballo:" sia i dati grezzi
     * per la riga "Imballi per pallet" (numerosità, peso pallet, UM) — così
     * da non dover aggiungere nuovi campi a ListinoRow: ListinoPdfBuilder
     * fa lo split su questo carattere e localizza/formatta la terza riga.
     * Carattere di controllo (BEL, \u0007), impossibile che compaia in un
     * testo di descrizione imballo reale.
     */
    public static final String PACKAGING_NOTE_SEPARATOR = "\u0007";

    public static class Imbal3Info {
        public final Specificity specificity;
        private final String composedTextIt;
        private final String composedTextEn;

        Imbal3Info(Specificity specificity, String composedTextIt, String composedTextEn) {
            this.specificity     = specificity;
            this.composedTextIt  = composedTextIt;
            this.composedTextEn  = composedTextEn;
        }

        /**
         * Testo composto nella lingua richiesta: "DescImballo1 DescImballo2
         * (Quantità UM)", con in coda — separati da {@link #PACKAGING_NOTE_SEPARATOR}
         * e presenti solo se numerosità &gt; 0 — numerosità, peso pallet
         * calcolato e UM, per la riga "Imballi per pallet" in stampa.
         */
        public String getText(String language) {
            return "EN".equalsIgnoreCase(language) ? composedTextEn : composedTextIt;
        }
    }

    private final S4HttpClient http;

    // Se il servizio non è (ancora) raggiungibile, si disattiva senza
    // bloccare l'estrazione: l'imballo semplicemente risulta "mancante".
    private volatile boolean available = true;

    public Imbal3ReadClient(S4HttpClient http) { this.http = http; }

    /**
     * Record grezzo di un'associazione ZIMBAL_3 (per il popup di
     * attribuzione — a differenza di {@link Imbal3Info}, qui servono i
     * singoli campi non ancora composti in un testo unico, per poterli
     * ripresentare come valori editabili nel form).
     */
    public static class AssociationRecord {
        public final Specificity specificity;
        public final String codImballo;
        public final String codImballo2;
        public final String meins;
        public final double quantita;
        public final int numerosita;

        AssociationRecord(Specificity specificity, String codImballo, String codImballo2,
                           String meins, double quantita, int numerosita) {
            this.specificity = specificity;
            this.codImballo  = codImballo;
            this.codImballo2 = codImballo2;
            this.meins       = meins;
            this.quantita    = quantita;
            this.numerosita  = numerosita;
        }
    }

    /**
     * Riga grezza di un'associazione ZIMBAL_3 restituita da {@link #search} —
     * a differenza di {@link #fetch}/{@link #fetchAssociation} qui NON c'è
     * cascata: ogni riga trovata dal filtro viene restituita così com'è
     * (comprese eventuali righe specifiche e la riga generica per lo stesso
     * materiale, entrambe visibili separatamente in griglia).
     */
    public static class SearchRow {
        public final String customer;   // vuoto = riga generica
        public final String material;
        public final Specificity specificity;
        public final String codImballo;
        public final String codImballo2;
        public final String descrIt1, descrEn1, descrIt2, descrEn2;
        public final String meins;
        public final double quantita;
        public final int    numerosita;

        SearchRow(String customer, String material, Specificity specificity,
                  String codImballo, String codImballo2,
                  String descrIt1, String descrEn1, String descrIt2, String descrEn2,
                  String meins, double quantita, int numerosita) {
            this.customer    = customer;
            this.material    = material;
            this.specificity = specificity;
            this.codImballo  = codImballo;
            this.codImballo2 = codImballo2;
            this.descrIt1 = descrIt1; this.descrEn1 = descrEn1;
            this.descrIt2 = descrIt2; this.descrEn2 = descrEn2;
            this.meins       = meins;
            this.quantita    = quantita;
            this.numerosita  = numerosita;
        }

        public String getImballoText(String language) {
            String d1 = "EN".equalsIgnoreCase(language) ? descrEn1 : descrIt1;
            String d2 = "EN".equalsIgnoreCase(language) ? descrEn2 : descrIt2;
            StringBuilder sb = new StringBuilder();
            if (d1 != null && !d1.isBlank()) sb.append(d1.trim());
            if (d2 != null && !d2.isBlank()) { if (sb.length() > 0) sb.append(" "); sb.append(d2.trim()); }
            return sb.toString();
        }
    }

    /**
     * Cerca le associazioni ZIMBAL_3 per Cliente e/o Materiale — usata dalla
     * pagina di manutenzione dedicata (ImballiUI), che parte senza dati e si
     * popola solo su ricerca. Richiede almeno uno dei due criteri (nessun
     * elenco completo non filtrato, per non caricare l'intera tabella).
     *
     * @param customer se valorizzato, filtra sul cliente esatto (stringa vuota
     *                 nel filtro trova solo le righe generiche — non è questo
     *                 il caso d'uso qui: per cercare "solo generiche" passare
     *                 customer=null/blank e material valorizzato, poi guardare
     *                 le righe con {@code specificity == GENERIC} nel risultato)
     * @param material se valorizzato, filtra sul materiale esatto
     */
    public List<SearchRow> search(String customer, String material)
            throws IOException, InterruptedException {
        List<SearchRow> result = new ArrayList<>();
        boolean hasCustomer = customer != null && !customer.isBlank();
        boolean hasMaterial = material != null && !material.isBlank();
        if (!hasCustomer && !hasMaterial) return result;

        StringBuilder filter = new StringBuilder("IsActiveEntity eq true");
        if (hasCustomer) filter.append(" and cliente eq '").append(customer.trim()).append("'");
        if (hasMaterial) filter.append(" and materiale eq '").append(material.trim()).append("'");

        String path = ENTITY_PATH
            + "?$filter=" + S4HttpClient.encode(filter.toString())
            + "&$select=cliente,materiale,cod_imballo,cod_imballo2,"
            + "descr_text_it_1,descr_text_en_1,descr_text_it_2,descr_text_en_2,meins,quantita,numerosita"
            + "&$orderby=cliente,materiale";

        JsonNode root = http.getOData(path);
        JsonNode results = root.path("value");
        if (results.isArray()) {
            for (JsonNode n : results) {
                String cust = n.path("cliente").asText("").trim();
                result.add(new SearchRow(
                    cust,
                    n.path("materiale").asText("").trim(),
                    cust.isBlank() ? Specificity.GENERIC : Specificity.SPECIFIC,
                    n.path("cod_imballo").asText(""),
                    n.path("cod_imballo2").asText(""),
                    n.path("descr_text_it_1").asText(""), n.path("descr_text_en_1").asText(""),
                    n.path("descr_text_it_2").asText(""), n.path("descr_text_en_2").asText(""),
                    n.path("meins").asText(""),
                    n.path("quantita").asDouble(0d),
                    n.path("numerosita").asInt(0)));
            }
        }
        return result;
    }

    /**
     * Legge l'associazione (se esiste) per una singola coppia cliente/materiale,
     * con la stessa cascata Specifico → Generico usata in {@link #fetch}, ma
     * restituendo i campi grezzi invece del testo già composto — usato per
     * pre-compilare il popup di attribuzione in modalità modifica.
     * Restituisce {@code null} se non esiste nessuna associazione (né
     * specifica né generica).
     */
    public AssociationRecord fetchAssociation(String customer, String material)
            throws IOException, InterruptedException {
        if (!available) return null;

        String filter = "IsActiveEntity eq true and ("
            + "(cliente eq '" + customer + "' and materiale eq '" + material + "')"
            + " or (cliente eq '' and materiale eq '" + material + "')"
            + ")";

        String path = ENTITY_PATH
            + "?$filter=" + S4HttpClient.encode(filter)
            + "&$select=cliente,materiale,cod_imballo,cod_imballo2,meins,quantita,numerosita";

        JsonNode root;
        try {
            root = http.getOData(path);
        } catch (IOException e) {
            available = false;
            System.err.println("Imbal3ReadClient: servizio non raggiungibile durante fetchAssociation: " + e.getMessage());
            return null;
        }

        JsonNode results = root.path("value");
        JsonNode specificRow = null, genericRow = null;
        if (results.isArray()) {
            for (JsonNode n : results) {
                String c = n.path("cliente").asText("").trim();
                if (c.isBlank()) genericRow = n; else specificRow = n;
            }
        }
        JsonNode row = specificRow != null ? specificRow : genericRow;
        if (row == null) return null;

        return new AssociationRecord(
            specificRow != null ? Specificity.SPECIFIC : Specificity.GENERIC,
            row.path("cod_imballo").asText(""),
            row.path("cod_imballo2").asText(""),
            row.path("meins").asText(""),
            row.path("quantita").asDouble(0d),
            row.path("numerosita").asInt(0));
    }

    /**
     * @param pairs coppie {customer, material}
     * @return mappa "customer|material" → Imbal3Info, sempre presente per
     *         ogni coppia richiesta (anche quando il risultato è MISSING)
     */
    public Map<String, Imbal3Info> fetch(List<String[]> pairs) throws IOException, InterruptedException {
        Map<String, Imbal3Info> result = new HashMap<>();
        if (pairs == null || pairs.isEmpty()) return result;

        // Risultati grezzi: specifico indicizzato per "cust|mat", generico per "mat"
        Map<String, JsonNode> specific = new HashMap<>();
        Map<String, JsonNode> generic  = new HashMap<>();

        if (available) {
            for (int i = 0; i < pairs.size(); i += BATCH_SIZE) {
                List<String[]> batch = pairs.subList(i, Math.min(i + BATCH_SIZE, pairs.size()));
                fetchBatch(batch, specific, generic);
            }
        }

        for (String[] pair : pairs) {
            String customer = pair[0];
            String material = pair[1];
            String key = customer + "|" + material;

            JsonNode row  = specific.get(key);
            Specificity spec = Specificity.SPECIFIC;
            if (row == null) {
                row  = generic.get(material);
                spec = Specificity.GENERIC;
            }
            if (row == null) {
                result.put(key, new Imbal3Info(Specificity.MISSING, "", ""));
                continue;
            }
            String textIt = composeText(row, "descr_text_it_1", "descr_text_it_2");
            String textEn = composeText(row, "descr_text_en_1", "descr_text_en_2");

            int    numerosita   = row.path("numerosita").asInt(0);
            double quantitaBag  = row.path("quantita").asDouble(0d);
            String meins        = nvl(row.path("meins").asText(null));
            if (numerosita > 0) {
                double palletWeight = quantitaBag * numerosita;
                String suffix = PACKAGING_NOTE_SEPARATOR + numerosita
                    + PACKAGING_NOTE_SEPARATOR + formatNumber(palletWeight)
                    + PACKAGING_NOTE_SEPARATOR + meins.trim();
                textIt += suffix;
                textEn += suffix;
            }

            result.put(key, new Imbal3Info(spec, textIt, textEn));
        }
        return result;
    }

    private String composeText(JsonNode row, String field1, String field2) {
        String d1  = nvl(row.path(field1).asText(null));
        String d2  = nvl(row.path(field2).asText(null));
        double qty = row.path("quantita").asDouble(0d);
        String um  = nvl(row.path("meins").asText(null));

        StringBuilder sb = new StringBuilder();
        if (!d1.isBlank()) sb.append(d1.trim());
        if (!d2.isBlank()) { if (sb.length() > 0) sb.append(" "); sb.append(d2.trim()); }
        if (qty > 0d) {
            String qtyStr = (qty == Math.floor(qty))
                ? String.format("%,.0f", qty)
                : String.format("%,.3f", qty);
            if (sb.length() > 0) sb.append(" ");
            sb.append("(").append(qtyStr);
            if (!um.isBlank()) sb.append(" ").append(um.trim());
            sb.append(")");
        }
        return sb.toString();
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private static String formatNumber(double v) {
        return (v == Math.floor(v)) ? String.format("%,.0f", v) : String.format("%,.3f", v);
    }

    private void fetchBatch(List<String[]> pairs, Map<String, JsonNode> specific, Map<String, JsonNode> generic)
            throws IOException, InterruptedException {

        StringBuilder filter = new StringBuilder();
        filter.append("IsActiveEntity eq true and (");
        boolean first = true;
        for (String[] pair : pairs) {
            if (!first) filter.append(" or ");
            filter.append("(cliente eq '").append(pair[0]).append("' and materiale eq '").append(pair[1]).append("')")
                  .append(" or (cliente eq '' and materiale eq '").append(pair[1]).append("')");
            first = false;
        }
        filter.append(")");

        String path = ENTITY_PATH
            + "?$filter=" + S4HttpClient.encode(filter.toString())
            + "&$select=cliente,materiale,cod_imballo,cod_imballo2,"
            + "descr_text_it_1,descr_text_en_1,descr_text_it_2,descr_text_en_2,meins,quantita,numerosita";

        try {
            JsonNode root = http.getOData(path);
            // OData V4: la lista è in "value", non in "d.results" come nelle API V2
            JsonNode results = root.path("value");
            if (results.isArray()) {
                for (JsonNode n : results) {
                    String customer = n.path("cliente").asText("").trim();
                    String material = n.path("materiale").asText("").trim();
                    if (material.isBlank()) continue;
                    if (customer.isBlank()) {
                        generic.put(material, n);
                    } else {
                        specific.put(customer + "|" + material, n);
                    }
                }
            }
            System.out.println("Imbal3ReadClient: batch — trovate " + results.size()
                + " righe (specifiche+generiche) su " + pairs.size() + " coppie richieste");
        } catch (IOException e) {
            available = false;
            System.err.println("Imbal3ReadClient: servizio ZI_ZIMBAL_3/ImballoClienteMat "
                + "non raggiungibile — disattivato per il resto dell'estrazione (imballo mancante "
                + "per tutte le righe). Dettaglio: " + e.getMessage());
        }
    }
}
