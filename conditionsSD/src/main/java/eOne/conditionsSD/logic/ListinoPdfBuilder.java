package eOne.conditionsSD.logic;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import eOne.conditionsSD.model.ListinoRow;

/**
 * Costruisce il PDF "carta intestata" del listino a partire dalle righe
 * già estratte da {@link ListinoExtractor} / {@link ListinoBuilder}.
 * Usa OpenPDF (com.lowagie.text.*), già presente in classpath come
 * dipendenza transitiva di eclntjsfserverRISC_jakarta (è la stessa libreria
 * usata internamente da FIXGRIDPDFExporter).
 *
 * I testi "di struttura" del documento (titolo, etichette colonne, ecc.)
 * sono localizzati in base alla lingua del cliente (ListinoRow.getLanguage()).
 * Le descrizioni materiali/zone NON vengono mai tradotte (sono dati SAP).
 * Il piè di pagina con i dati legali aziendali resta sempre in italiano.
 */
public class ListinoPdfBuilder {

    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ═══════════════════════════════════════════════════════════════════════
    // Dati azienda — Lamplast
    // ═══════════════════════════════════════════════════════════════════════
    private static final String LOGO_RESOURCE = "/eOne/assets/logo_lamplast.jpg";

    private static final String[] COMPANY_LINES = {
        "LAMPLAST s.r.l. di Aldo Redaelli & C.",
        "Capitale Sociale 1.000.000 Eur interamente versato",
        "20833 Giussano (MB) Italia - fraz. Molino Principe",
        "Tel. 0362 3537.1  Fax 0362 852409",
        "e-mail: lamplast@lamplast.it - sito web: http://www.lamplast.it"
    };

    private static final String[] FOOTER_LINES = {
        "Sede Soc. e legale: Giussano (MB) - Ufficio Reg. delle imprese di Milano n. 02660370152 - REA MB-839731",
        "C.F. 02660370152 - Part. IVA - EORI IT00736790965 - Registrazione REX: ITREXIT00736790965"
    };

    // ═══════════════════════════════════════════════════════════════════════
    // Font / colori
    // ═══════════════════════════════════════════════════════════════════════
    private static final Font F_COMPANY   = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.DARK_GRAY);
    private static final Font F_TITLE     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
    private static final Font F_SUBTITLE  = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font F_CUSTOMER  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(0x15, 0x65, 0xC0));
    private static final Font F_CUSTOMER_EXTRA = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.DARK_GRAY);
    private static final Font F_ZONE_HDR  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(0x55, 0x55, 0x55));
    private static final Font F_MATERIAL  = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    private static final Font F_MATERIAL_NOTE = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 6.5f, Color.DARK_GRAY);
    private static final Font F_ZONE_REF  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(0x00, 0x77, 0x00));
    private static final Font F_ZONE      = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(0x88, 0x88, 0x88));
    private static final Font F_UM_WARN   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(0xCC, 0x00, 0x00));
    private static final Font F_ALERT_HDR = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(0xCC, 0x66, 0x00));
    private static final Font F_ALERT     = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(0xCC, 0x66, 0x00));
    private static final Font F_TABLE_HDR = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE);
    private static final Font F_FOOTER    = FontFactory.getFont(FontFactory.HELVETICA, 6, Color.GRAY);
    private static final Font F_PAGENO    = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);

    private static final Color COLOR_TABLE_HDR_BG = new Color(0x15, 0x65, 0xC0);
    private static final Color COLOR_ROW_ALT_BG   = new Color(0xF4, 0xF7, 0xFB);

    private static final float MARGIN_LEFT   = 34f;
    private static final float MARGIN_RIGHT  = 34f;
    private static final float MARGIN_TOP    = 95f;   // spazio per logo + dati azienda + separatore
    private static final float MARGIN_BOTTOM = 44f;   // spazio per piede pagina

    private byte[] logoBytes;

    // ═══════════════════════════════════════════════════════════════════════
    // Localizzazione (IT/EN) dei testi di struttura del documento
    // ═══════════════════════════════════════════════════════════════════════

    private static final class Labels {
        boolean english;
        String title, refDate, emissionDate, colMaterial, colZone, colCodCliente,
               colDiv, colPer, colUM, colDa, colA, zoneAlternative, standardDelivery,
               qualsiasi, finoA, page, minLot, packaging, paymentTerms, incoterms;

        static Labels it() {
            Labels l = new Labels();
            l.english          = false;
            l.title            = "Listino Prezzi di Vendita";
            l.refDate          = "Data di riferimento condizioni:";
            l.emissionDate     = "Data emissione:";
            l.colMaterial      = "Materiale";
            l.colZone          = "Zona";
            l.colCodCliente    = "Cod. cliente";
            l.colDiv           = "Div.";
            l.colPer           = "Per";
            l.colUM            = "UM";
            l.colDa            = "Da";
            l.colA             = "A";
            l.zoneAlternative  = "Zone alternative";
            l.standardDelivery = "consegna standard";
            l.qualsiasi        = "Qualsiasi";
            l.finoA            = "fino a";
            l.page             = "Pag.";
            l.minLot           = "Lotto minimo:";
            l.packaging        = "Imballo preferenziale:";
            l.paymentTerms     = "Condizioni di pagamento:";
            l.incoterms        = "Incoterms:";
            return l;
        }

        static Labels en() {
            Labels l = new Labels();
            l.english          = true;
            l.title            = "Sales Price List";
            l.refDate          = "Conditions reference date:";
            l.emissionDate     = "Issue date:";
            l.colMaterial      = "Material";
            l.colZone          = "Zone";
            l.colCodCliente    = "Customer mat. code";
            l.colDiv           = "Cur.";
            l.colPer           = "Per";
            l.colUM            = "UoM";
            l.colDa            = "From";
            l.colA             = "To";
            l.zoneAlternative  = "Alternative zones";
            l.standardDelivery = "standard delivery";
            l.qualsiasi        = "Any";
            l.finoA            = "up to";
            l.page             = "Page";
            l.minLot           = "Minimum lot:";
            l.packaging        = "Preferred packaging:";
            l.paymentTerms     = "Payment terms:";
            l.incoterms        = "Incoterms:";
            return l;
        }

        static Labels forLanguage(String lang) {
            if (lang != null) {
                String l = lang.trim().toUpperCase();
                if (l.equals("EN") || l.equals("E")) return en();
            }
            return it(); // default italiano
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Entry point
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Genera il PDF: raggruppa le righe per cliente e produce, per ciascun
     * cliente, un'unica PdfPTable con header colonne ripetuto su ogni
     * pagina (setHeaderRows), separata da un salto pagina dal cliente
     * successivo. Titolo, sottotitolo ed etichette colonna sono localizzati
     * per lingua del singolo cliente (ListinoRow.getLanguage()).
     *
     * @param rows          righe del listino, nell'ordine già preparato da ListinoBuilder
     *                      (CUSTOMER, HEADER_SCALE, MATERIAL/HEADER_ZONE/ZONE, ALERT...)
     * @param referenceDate data di riferimento condizioni, mostrata nel titolo
     */
    public byte[] buildDocument(List<ListinoRow> rows, LocalDate referenceDate) throws Exception {

        loadLogo();

        Document document = new Document(PageSize.A4.rotate(), MARGIN_LEFT, MARGIN_RIGHT, MARGIN_TOP, MARGIN_BOTTOM);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        writer.setPageEvent(new LetterheadPageEvent());
        document.open();

        List<List<ListinoRow>> blocks = splitByCustomer(rows);
        boolean firstBlock = true;
        for (List<ListinoRow> block : blocks) {
            if (block.isEmpty()) continue;
            if (!firstBlock) document.newPage();
            firstBlock = false;
            renderCustomerBlock(document, block, referenceDate);
        }

        document.close();
        return baos.toByteArray();
    }

    private List<List<ListinoRow>> splitByCustomer(List<ListinoRow> rows) {
        List<List<ListinoRow>> blocks = new ArrayList<>();
        List<ListinoRow> current = null;
        for (ListinoRow row : rows) {
            if (row.isCustomerRow()) {
                current = new ArrayList<>();
                blocks.add(current);
            }
            if (current == null) {
                current = new ArrayList<>();
                blocks.add(current);
            }
            current.add(row);
        }
        return blocks;
    }

    private void renderCustomerBlock(Document document, List<ListinoRow> block, LocalDate referenceDate)
            throws DocumentException {

        ListinoRow customerRow = block.get(0);
        Labels labels = Labels.forLanguage(customerRow.isCustomerRow() ? customerRow.getLanguage() : null);

        // ── Titolo documento (localizzato per il cliente di questa pagina) ──
        Paragraph title = new Paragraph(labels.title, F_TITLE);
        title.setSpacingAfter(2f);
        document.add(title);

        String sub = labels.refDate + " " + (referenceDate != null ? referenceDate.format(FMT_DATE) : "-")
                   + "        " + labels.emissionDate + " " + LocalDateTime.now().format(FMT_DATETIME);
        Paragraph subtitle = new Paragraph(sub, F_SUBTITLE);
        subtitle.setSpacingAfter(14f);
        document.add(subtitle);

        if (customerRow.isCustomerRow()) {
            Paragraph custTitle = new Paragraph();
            custTitle.add(new Chunk(nvl(customerRow.getCustomerName()), F_CUSTOMER));

            StringBuilder extra = new StringBuilder();
            if (!nvl(customerRow.getPaymentTerms()).isBlank()) {
                extra.append("   ").append(labels.paymentTerms).append(' ').append(customerRow.getPaymentTerms());
            }
            String incoterms = (nvl(customerRow.getIncotermsClassification())
                + (nvl(customerRow.getIncotermsLocation()).isBlank() ? "" : " " + customerRow.getIncotermsLocation())).trim();
            if (!incoterms.isBlank()) {
                extra.append("   ").append(labels.incoterms).append(' ').append(incoterms);
            }
            if (extra.length() > 0) custTitle.add(new Chunk(extra.toString(), F_CUSTOMER_EXTRA));

            custTitle.setSpacingAfter(8f);
            document.add(custTitle);
        }

        PdfPTable table = null;
        boolean zoneSection = false;
        List<String> alertLines = new ArrayList<>();

        for (ListinoRow row : block) {
            if (row.isCustomerRow()) continue;

            if (row.isHeaderScaleRow()) {
                if (table != null) {
                    document.add(table);
                    addHorizontalRule(document);
                }
                if (zoneSection) {
                    Paragraph zoneLabel = new Paragraph(labels.zoneAlternative, F_ZONE_HDR);
                    zoneLabel.setSpacingBefore(6f);
                    zoneLabel.setSpacingAfter(2f);
                    document.add(zoneLabel);
                }
                table = newListinoTable();
                addTableHeader(table, row, labels, zoneSection);
                continue;
            }

            if (row.isMaterialRow()) {
                if (table == null) { table = newListinoTable(); addPlainHeader(table, labels, zoneSection); }
                addMaterialRow(table, row);
                addMaterialNoteRow(table, row, labels);
                continue;
            }

            if (row.isHeaderZoneRow()) {
                zoneSection = true; // la prossima HEADER_SCALE apre la tabella "zone";
                                     // l'etichetta viene stampata attaccata a quella tabella (vedi sopra)
                continue;
            }

            if (row.isZoneRow()) {
                if (table != null) addZoneRow(table, row, labels);
                continue;
            }

            if (row.isAlertRow()) {
                if (row.getCustomerCode() == null) {
                    alertLines.add(nvl(row.getDescription()));
                } else {
                    alertLines.add(" - " + nvl(row.getDescription()));
                }
            }
        }

        if (table != null) document.add(table);

        if (!alertLines.isEmpty()) {
            document.add(Chunk.NEWLINE);
            Paragraph alertHdr = new Paragraph(alertLines.get(0), F_ALERT_HDR);
            alertHdr.setSpacingAfter(3f);
            document.add(alertHdr);
            for (int i = 1; i < alertLines.size(); i++) {
                document.add(new Paragraph(alertLines.get(i), F_ALERT));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Tabella listino — costruzione colonne
    //
    // Ordine colonne: [0] Materiale/Zona (dinamica)  [1] Cod. cliente
    //                 [2..6] Scag. 1-5   [7] Div.  [8] Per  [9] UM  [10] Da  [11] A
    // ═══════════════════════════════════════════════════════════════════════

    private static final float[] COL_WIDTHS = {
        20f, 9f, 8f, 8f, 8f, 8f, 8f, 4f, 4f, 5f, 9f, 9f
    };

    private String[] colTitles(Labels labels, boolean zoneSection) {
        return new String[] {
            zoneSection ? labels.colZone : labels.colMaterial,
            labels.colCodCliente,
            "Scag. 1", "Scag. 2", "Scag. 3", "Scag. 4", "Scag. 5",
            labels.colDiv, labels.colPer, labels.colUM, labels.colDa, labels.colA
        };
    }

    private PdfPTable newListinoTable() throws DocumentException {
        PdfPTable table = new PdfPTable(COL_WIDTHS.length);
        table.setWidths(COL_WIDTHS);
        table.setWidthPercentage(100f);
        table.setHeaderRows(1);
        table.setSpacingBefore(2f);
        return table;
    }

    private void addPlainHeader(PdfPTable table, Labels labels, boolean zoneSection) {
        String[] titles = colTitles(labels, zoneSection);
        PdfPCell firstCell = headerCell(titles[0]);
        if (zoneSection) firstCell.setColspan(2);
        table.addCell(firstCell);
        int start = zoneSection ? 2 : 1;
        for (int i = start; i < titles.length; i++) {
            table.addCell(headerCell(titles[i]));
        }
    }

    /** Header con le soglie di scaglione effettive. Se una colonna non ha soglia reale
     *  (materiale non scaglionato su quella posizione), l'intestazione resta vuota
     *  ma la colonna viene comunque mantenuta per allineamento. */
    private void addTableHeader(PdfPTable table, ListinoRow headerScaleRow, Labels labels, boolean zoneSection) {
        String[] titles = colTitles(labels, zoneSection);
        PdfPCell firstCell = headerCell(titles[0]);
        if (zoneSection) firstCell.setColspan(2);
        table.addCell(firstCell);
        if (!zoneSection) table.addCell(headerCell(titles[1]));   // Cod. cliente: solo tabella materiali
        for (int n = 1; n <= 5; n++) {
            table.addCell(headerCell(formatScaleHeader(headerScaleRow, n, labels)));
        }
        for (int i = 7; i < titles.length; i++) {
            table.addCell(headerCell(titles[i]));
        }
    }

    private PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, F_TABLE_HDR));
        cell.setBackgroundColor(COLOR_TABLE_HDR_BG);
        cell.setPadding(4.5f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private int rowToggle = 0;

    private void addMaterialRow(PdfPTable table, ListinoRow row) {
        Color bg = ((rowToggle++ % 2) == 0) ? Color.WHITE : COLOR_ROW_ALT_BG;

        table.addCell(dataCell(nvl(row.getDescription()), F_MATERIAL, Element.ALIGN_LEFT, bg));
        table.addCell(dataCell(nvl(row.getCustomerMaterialCode()), F_MATERIAL, Element.ALIGN_LEFT, bg));
        for (int n = 1; n <= 5; n++) {
            String v = n > row.getActiveCols() ? "" : formatPrice(row, n);
            table.addCell(dataCell(v, F_MATERIAL, Element.ALIGN_RIGHT, bg));
        }
        table.addCell(dataCell(nvl(row.getCurrency()), F_MATERIAL, Element.ALIGN_CENTER, bg));
        table.addCell(dataCell(row.getConditionQty() > 0 ? String.format("%.0f", row.getConditionQty()) : "",
            F_MATERIAL, Element.ALIGN_RIGHT, bg));
        Font umFont = row.isUnitMismatch() ? F_UM_WARN : F_MATERIAL;
        table.addCell(dataCell(nvl(row.getConditionUnit()), umFont, Element.ALIGN_CENTER, bg));
        table.addCell(dataCell(row.getValidFrom() != null ? row.getValidFrom().format(FMT_DATE) : "",
            F_MATERIAL, Element.ALIGN_CENTER, bg));
        table.addCell(dataCell(row.getValidTo() != null ? row.getValidTo().format(FMT_DATE) : "",
            F_MATERIAL, Element.ALIGN_CENTER, bg));
    }

    /**
     * Seconda riga, subito sotto il materiale, con lotto minimo e/o imballo
     * preferenziale (Customer-Material Info Record) — solo se almeno una
     * delle due informazioni è presente. Occupa la colonna unificata
     * Materiale + Cod. cliente (colspan 2), il resto della riga resta vuoto.
     */
    private void addMaterialNoteRow(PdfPTable table, ListinoRow row, Labels labels) {
        String packaging = labels.english ? row.getPackagingNoteEN() : row.getPackagingNoteIT();
        boolean hasMinQty = row.getMinDeliveryQuantity() > 0d;
        boolean hasPackaging = packaging != null && !packaging.isBlank();
        if (!hasMinQty && !hasPackaging) return;

        StringBuilder sb = new StringBuilder("   ");
        if (hasMinQty) {
            sb.append(labels.minLot).append(' ').append(formatQty(row.getMinDeliveryQuantity()));
            if (!nvl(row.getConditionUnit()).isBlank()) sb.append(' ').append(row.getConditionUnit());
        }
        if (hasPackaging) {
            if (hasMinQty) sb.append("   —   ");
            sb.append(labels.packaging).append(' ').append(packaging.trim());
        }

        // stessa alternanza colore della riga materiale appena scritta (rowToggle già incrementato)
        Color bg = (((rowToggle - 1) % 2) == 0) ? Color.WHITE : COLOR_ROW_ALT_BG;

        PdfPCell noteCell = dataCell(sb.toString(), F_MATERIAL_NOTE, Element.ALIGN_LEFT, bg);
        noteCell.setColspan(2);
        noteCell.setPaddingTop(0f);
        table.addCell(noteCell);
        for (int i = 0; i < 10; i++) table.addCell(dataCell("", F_MATERIAL_NOTE, Element.ALIGN_CENTER, bg));
    }

    private void addZoneRow(PdfPTable table, ListinoRow row, Labels labels) {
        Color bg = ((rowToggle++ % 2) == 0) ? Color.WHITE : COLOR_ROW_ALT_BG;
        Font font = row.isPreferredZone() ? F_ZONE_REF : F_ZONE;

        String desc = "   " + nvl(row.getDescription());
        if (row.isPreferredZone()) desc += "  (" + labels.standardDelivery + ")";
        PdfPCell descCell = dataCell(desc, font, Element.ALIGN_LEFT, bg);
        descCell.setColspan(2); // estende la colonna descrizione al posto di "Cod. cliente", non pertinente per le zone
        table.addCell(descCell);

        for (int n = 1; n <= 5; n++) {
            // La zona di default (inclusa nel prezzo di vendita) non riporta alcun prezzo/delta
            String v = (row.isPreferredZone() || n > row.getActiveCols()) ? "" : formatDelta(row, n);
            table.addCell(dataCell(v, font, Element.ALIGN_RIGHT, bg));
        }
        table.addCell(dataCell(nvl(row.getCurrency()), font, Element.ALIGN_CENTER, bg));
        table.addCell(dataCell(row.getConditionQty() > 0 ? String.format("%.0f", row.getConditionQty()) : "",
            font, Element.ALIGN_RIGHT, bg));
        table.addCell(dataCell(nvl(row.getConditionUnit()), font, Element.ALIGN_CENTER, bg));
        table.addCell(dataCell(row.getValidFrom() != null ? row.getValidFrom().format(FMT_DATE) : "",
            font, Element.ALIGN_CENTER, bg));
        table.addCell(dataCell(row.getValidTo() != null ? row.getValidTo().format(FMT_DATE) : "",
            font, Element.ALIGN_CENTER, bg));
    }

    /** Riga orizzontale di chiusura, usata per separare la tabella prezzi dalla sezione zone. */
    private void addHorizontalRule(Document document) throws DocumentException {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100f);
        rule.setSpacingBefore(1f);
        rule.setSpacingAfter(0f);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(1.2f);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(1.2f);
        cell.setBorderColor(new Color(0x15, 0x65, 0xC0));
        cell.setPadding(0f);
        rule.addCell(cell);
        document.add(rule);
    }

    private PdfPCell dataCell(String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4f);
        cell.setHorizontalAlignment(align);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(new Color(0xE0, 0xE0, 0xE0));
        return cell;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Formattazione valori — replica la logica di ListinoBean.GridListinoItem
    // ═══════════════════════════════════════════════════════════════════════

    private String formatScaleHeader(ListinoRow row, int n, Labels labels) {
        double q = row.getScaleQty()[n - 1];
        String unit = nvl(row.getScaleUnit());
        if (q <= 0) {
            if (n == 5) return unit.trim().isEmpty() ? labels.qualsiasi : labels.qualsiasi + " (" + unit + ")";
            return "";
        }
        String qty = q == Math.floor(q)
            ? String.format("%.0f", q)
            : String.format("%.3f", q).replaceAll("0+$", "");
        return labels.finoA + " " + qty + (unit.trim().isEmpty() ? "" : " " + unit);
    }

    private String formatPrice(ListinoRow row, int n) {
        double v = row.getPrice()[n - 1];
        return v != 0.0 ? String.format("%,.2f", v) : "";
    }

    private String formatDelta(ListinoRow row, int n) {
        double v = row.getPrice()[n - 1];
        if (row.isAbsolutePrice()) {
            return v != 0.0 ? String.format("%,.2f", v) : "—";
        }
        return v != 0.0 ? String.format("%+,.2f", v) : "—";
    }

    private String formatQty(double q) {
        return q == Math.floor(q)
            ? String.format("%.0f", q)
            : String.format("%.3f", q).replaceAll("0+$", "");
    }

    private String nvl(String s) { return s != null ? s : ""; }

    // ═══════════════════════════════════════════════════════════════════════
    // Logo
    // ═══════════════════════════════════════════════════════════════════════

    private void loadLogo() throws Exception {
        if (logoBytes != null) return;
        try (InputStream is = getClass().getResourceAsStream(LOGO_RESOURCE)) {
            if (is == null) throw new IllegalStateException("Logo non trovato: " + LOGO_RESOURCE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            logoBytes = out.toByteArray();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Header / Footer pagina — carta intestata
    // ═══════════════════════════════════════════════════════════════════════

    private class LetterheadPageEvent extends PdfPageEventHelper {

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte cb = writer.getDirectContent();
                Rectangle page = document.getPageSize();
                float pageWidth = page.getWidth();
                float left = document.leftMargin();
                float right = pageWidth - document.rightMargin();
                float top = page.getHeight() - 28f;

                // ── Logo (alto a sinistra) ──────────────────────────────
                float logoWidth = 155f;
                float logoHeight = 0f;
                if (logoBytes != null) {
                    Image logo = Image.getInstance(logoBytes);
                    logoHeight = logoWidth * logo.getHeight() / logo.getWidth();
                    logo.scaleAbsolute(logoWidth, logoHeight);
                    logo.setAbsolutePosition(left, top - logoHeight);
                    cb.addImage(logo);
                }

                // ── Dati azienda (stesso livello, a destra) ─────────────
                float blockWidth = 235f;
                float companyBlockHeight = COMPANY_LINES.length * 9f;
                float companyTop = top - Math.max(0f, (logoHeight - companyBlockHeight) / 2f);

                ColumnText ct = new ColumnText(cb);
                ct.setSimpleColumn(right - blockWidth, companyTop - companyBlockHeight - 4f,
                    right, companyTop + 4f);
                ct.setAlignment(Element.ALIGN_RIGHT);
                Paragraph companyPar = new Paragraph();
                companyPar.setLeading(9f);
                for (String line : COMPANY_LINES) {
                    companyPar.add(new Chunk(line, F_COMPANY));
                    companyPar.add(Chunk.NEWLINE);
                }
                ct.addElement(companyPar);
                ct.go();

                // ── Separatore ───────────────────────────────────────────
                float headerBottom = top - Math.max(logoHeight, companyBlockHeight) - 8f;
                cb.setLineWidth(0.6f);
                cb.setColorStroke(new Color(0x15, 0x65, 0xC0));
                cb.moveTo(left, headerBottom);
                cb.lineTo(right, headerBottom);
                cb.stroke();

                // ── Piè di pagina (sempre in italiano: dati legali aziendali) ──
                float footerTop = document.bottomMargin() - 10f;
                ColumnText ctf = new ColumnText(cb);
                ctf.setSimpleColumn(left, 8f, right, footerTop);
                ctf.setAlignment(Element.ALIGN_CENTER);
                Paragraph footerPar = new Paragraph();
                footerPar.setLeading(8f);
                for (String line : FOOTER_LINES) {
                    footerPar.add(new Chunk(line, F_FOOTER));
                    footerPar.add(Chunk.NEWLINE);
                }
                ctf.addElement(footerPar);
                ctf.go();

                cb.setLineWidth(0.4f);
                cb.setColorStroke(Color.LIGHT_GRAY);
                cb.moveTo(left, footerTop + 2f);
                cb.lineTo(right, footerTop + 2f);
                cb.stroke();

                // ── Numero pagina ────────────────────────────────────────
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Pag. " + writer.getPageNumber(), F_PAGENO),
                    right, footerTop + 2f, 0);

            } catch (Exception e) {
                // non blocchiamo la generazione del documento per un problema di header/footer
                e.printStackTrace();
            }
        }
    }
}
