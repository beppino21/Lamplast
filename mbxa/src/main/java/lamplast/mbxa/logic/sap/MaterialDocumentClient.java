package lamplast.mbxa.logic.sap;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import lamplast.mbxa.logic.config.AppConfig;

/**
 * Lettura dei documenti materiale da API_MATERIAL_DOCUMENT_SRV (SAP_COM_0108).
 * <p>
 * Nota: il numero bolla (GoodsIssueOrReceiptSlipNumber / XABLN) NON è esposto da questa API
 * né da alcuna view rilasciata: in modo MANUALE il numero DDT lo inserisce l'operatore.
 */
public class MaterialDocumentClient {

    private final S4HttpClient m_http;
    private final String m_service;

    public MaterialDocumentClient(AppConfig cfg) {
        this(new S4HttpClient(cfg.getSapBaseUrl(), cfg.getSapUser(), cfg.getSapPassword(),
            cfg.getHttpTimeoutSeconds()), cfg.getMaterialDocumentServicePath());
    }

    public MaterialDocumentClient(S4HttpClient http, String servicePath) {
        m_http = http;
        m_service = servicePath;
    }

    /** Sintesi di un 301 per l'elenco "da acquisire". */
    public static class Summary implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public String year;
        public String number;
        public LocalDate postingDate;
        public String plant;
        public String receivingPlant;
        public int itemCount;
        public boolean cancelled;
        /** Testo di testata del documento materiale (MIGO). */
        public String headerText;
        /** Numero del DDT annullato che il nuovo DDT sostituirà (valorizzato da DdtService). */
        public String sostituisceDdt;
    }

    // ------------------------------------------------------------------------
    // lettura singolo documento
    // ------------------------------------------------------------------------

    /** Documento con tutte le righe; vuoto se il documento non esiste. */
    public Optional<SapMaterialDocument> read(String year, String number)
            throws IOException, InterruptedException {
        String path = m_service + "/A_MaterialDocumentHeader(MaterialDocumentYear="
            + S4HttpClient.lit(year) + ",MaterialDocument=" + S4HttpClient.lit(number) + ")"
            + "?$expand=to_MaterialDocumentItem";
        JsonNode d;
        try {
            d = m_http.get(path);
        } catch (S4HttpClient.ODataException e) {
            if (e.getStatus() == 404) return Optional.empty();
            throw e;
        }
        return Optional.of(parseDocument(d));
    }

    /**
     * Documento di storno (302) che fa riferimento al documento indicato.
     * Restituisce {anno, numero} oppure vuoto se non trovato.
     */
    public Optional<String[]> findReversal(String year, String number)
            throws IOException, InterruptedException {
        String filter = "ReversedMaterialDocumentYear eq " + S4HttpClient.lit(year)
            + " and ReversedMaterialDocument eq " + S4HttpClient.lit(number);
        String path = m_service + "/A_MaterialDocumentItem?$filter=" + S4HttpClient.enc(filter)
            + "&$select=MaterialDocumentYear,MaterialDocument&$top=1";
        JsonNode rows = S4HttpClient.results(m_http.get(path));
        if (rows.size() == 0) return Optional.empty();
        JsonNode r = rows.get(0);
        return Optional.of(new String[] {
            S4HttpClient.text(r, "MaterialDocumentYear"), S4HttpClient.text(r, "MaterialDocument") });
    }

    // ------------------------------------------------------------------------
    // elenco 301 recenti
    // ------------------------------------------------------------------------

    /**
     * 301 registrati da {@code fromDate} in poi (righe di uscita, più recenti prima).
     * Filtra per anno del documento e tipo movimento lato SAP, per data lato client.
     */
    public List<Summary> listRecent301(LocalDate fromDate, int max) throws IOException, InterruptedException {
        Map<String, Summary> byDoc = new LinkedHashMap<>();
        int fromYear = fromDate.getYear();
        int toYear = LocalDate.now().getYear();
        for (int y = toYear; y >= fromYear; y--) {
            String filter = "GoodsMovementType eq '301' and DebitCreditCode eq 'H'"
                + " and MaterialDocumentYear eq " + S4HttpClient.lit(String.valueOf(y));
            String path = m_service + "/A_MaterialDocumentItem?$filter=" + S4HttpClient.enc(filter)
                + "&$expand=to_MaterialDocumentHeader"
                + "&$orderby=" + S4HttpClient.enc("MaterialDocument desc")
                + "&$top=" + max;
            for (JsonNode r : S4HttpClient.results(m_http.get(path))) {
                if ("X".equalsIgnoreCase(S4HttpClient.text(r, "IsAutomaticallyCreated"))) continue;
                JsonNode h = r.path("to_MaterialDocumentHeader");
                LocalDate posting = S4HttpClient.parseDate(S4HttpClient.text(h, "PostingDate"));
                if (posting != null && posting.isBefore(fromDate)) continue;
                String key = S4HttpClient.text(r, "MaterialDocumentYear") + "/" + S4HttpClient.text(r, "MaterialDocument");
                Summary s = byDoc.get(key);
                if (s == null) {
                    s = new Summary();
                    s.year = S4HttpClient.text(r, "MaterialDocumentYear");
                    s.number = S4HttpClient.text(r, "MaterialDocument");
                    s.postingDate = posting;
                    s.plant = S4HttpClient.text(r, "Plant");
                    s.receivingPlant = S4HttpClient.text(r, "IssuingOrReceivingPlant");
                    s.headerText = S4HttpClient.text(h, "MaterialDocumentHeaderText");
                    byDoc.put(key, s);
                }
                s.itemCount++;
                if (r.path("GoodsMovementIsCancelled").asBoolean(false)) s.cancelled = true;
            }
        }
        List<Summary> result = new ArrayList<>(byDoc.values());
        result.sort((a, b) -> {
            int c = compareNullable(b.postingDate, a.postingDate);
            return c != 0 ? c : b.number.compareTo(a.number);
        });
        return result;
    }

    private static int compareNullable(LocalDate a, LocalDate b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    // ------------------------------------------------------------------------
    // parsing
    // ------------------------------------------------------------------------

    static SapMaterialDocument parseDocument(JsonNode d) {
        SapMaterialDocument doc = new SapMaterialDocument();
        doc.year = S4HttpClient.text(d, "MaterialDocumentYear");
        doc.number = S4HttpClient.text(d, "MaterialDocument");
        doc.postingDate = S4HttpClient.parseDate(S4HttpClient.text(d, "PostingDate"));
        doc.documentDate = S4HttpClient.parseDate(S4HttpClient.text(d, "DocumentDate"));
        doc.creationDate = S4HttpClient.parseDate(S4HttpClient.text(d, "CreationDate"));
        doc.creationTime = S4HttpClient.parseTime(S4HttpClient.text(d, "CreationTime"));
        doc.createdByUser = S4HttpClient.text(d, "CreatedByUser");
        doc.headerText = S4HttpClient.text(d, "MaterialDocumentHeaderText");
        doc.referenceDocument = S4HttpClient.text(d, "ReferenceDocument");
        for (JsonNode r : S4HttpClient.results(d.path("to_MaterialDocumentItem"))) {
            SapMaterialDocument.Item it = new SapMaterialDocument.Item();
            it.item = S4HttpClient.text(r, "MaterialDocumentItem");
            it.material = S4HttpClient.text(r, "Material");
            it.plant = S4HttpClient.text(r, "Plant");
            it.storageLocation = S4HttpClient.text(r, "StorageLocation");
            it.batch = S4HttpClient.text(r, "Batch");
            it.movementType = S4HttpClient.text(r, "GoodsMovementType");
            it.debitCredit = S4HttpClient.text(r, "DebitCreditCode");
            it.autoCreated = S4HttpClient.text(r, "IsAutomaticallyCreated");
            it.receivingPlant = S4HttpClient.text(r, "IssuingOrReceivingPlant");
            it.receivingStorageLoc = S4HttpClient.text(r, "IssuingOrReceivingStorageLoc");
            it.qtyEntry = decimal(r, "QuantityInEntryUnit");
            it.entryUnit = S4HttpClient.text(r, "EntryUnit");
            it.qtyBase = decimal(r, "QuantityInBaseUnit");
            it.baseUnit = firstNonNull(S4HttpClient.text(r, "MaterialBaseUnit"),
                S4HttpClient.text(r, "MaterialBaseUnitSAPCode"));
            it.cancelled = r.path("GoodsMovementIsCancelled").asBoolean(false);
            it.itemText = S4HttpClient.text(r, "MaterialDocumentItemText");
            doc.items.add(it);
        }
        doc.items.sort((a, b) -> a.item.compareTo(b.item));
        return doc;
    }

    private static BigDecimal decimal(JsonNode n, String field) {
        String s = S4HttpClient.text(n, field);
        if (s == null) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
