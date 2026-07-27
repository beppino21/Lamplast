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

    private static final String DOCUMENT_TITLE = "Listino Prezzi di Vendita";

    // ═══════════════════════════════════════════════════════════════════════
    // Font / colori
    // ═══════════════════════════════════════════════════════════════════════
    private static final Font F_COMPANY   = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.DARK_GRAY);
    private static final Font F_TITLE     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
    private static final Font F_SUBTITLE  = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font F_CUSTOMER  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(0x15, 0x65, 0xC0));
    private static final Font F_SCALE_HDR = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(0x15, 0x65, 0xC0));
    private static final Font F_ZONE_HDR  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(0x55, 0x55, 0x55));
    private static final Font F_MATERIAL  = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
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
    // Entry point
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Genera il PDF: raggruppa le righe per cliente e produce, per ciascun
     * cliente, un'unica PdfPTable con header colonne ripetuto su ogni
     * pagina (setHeaderRows), separata da un salto pagina dal cliente
     * successivo.
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

        Paragraph title = new Paragraph(DOCUMENT_TITLE, F_TITLE);
        title.setSpacingAfter(2f);
        document.add(title);

        String sub = "Data di riferimento condizioni: " + (referenceDate != null ? referenceDate.format(FMT_DATE) : "-")
                   + "        Data emissione: " + LocalDateTime.now().format(FMT_DATETIME);
        Paragraph subtitle = new Paragraph(sub, F_SUBTITLE);
        subtitle.setSpacingAfter(14f);
        document.add(subtitle);

        List<List<ListinoRow>> blocks = splitByCustomer(rows);
        boolean firstBlock = true;
        for (List<ListinoRow> block : blocks) {
            if (block.isEmpty()) continue;
            if (!firstBlock) document.newPage();
            firstBlock = false;
            renderCustomerBlock(document, block);
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

    private void renderCustomerBlock(Document document, List<ListinoRow> block) throws DocumentException {
        ListinoRow customerRow = block.get(0);
        if (customerRow.isCustomerRow()) {
            Paragraph custTitle = new Paragraph(nvl(customerRow.getCustomerName()), F_CUSTOMER);
            custTitle.setSpacingAfter(8f);
            document.add(custTitle);
        }

        PdfPTable table = null;
        List<String> alertLines = new ArrayList<>();

        for (ListinoRow row : block) {
            if (row.isCustomerRow()) continue;

            if (row.isHeaderScaleRow()) {
                if (table != null) { document.add(table); document.add(Chunk.NEWLINE); }
                table = newListinoTable();
                addTableHeader(table, row);
                continue;
            }

            if (row.isMaterialRow()) {
                if (table == null) { table = newListinoTable(); addPlainHeader(table); }
                addMaterialRow(table, row);
                continue;
            }

            if (row.isHeaderZoneRow()) {
                if (table != null) addFullWidthRow(table, "Zone alternative", F_ZONE_HDR, null);
                continue;
            }

            if (row.isZoneRow()) {
                if (table != null) addZoneRow(table, row);
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
    // ═══════════════════════════════════════════════════════════════════════

    private static final String[] COL_TITLES = {
        "Materiale / Zona", "Scag. 1", "Scag. 2", "Scag. 3", "Scag. 4", "Scag. 5",
        "Div.", "Per", "UM", "Da", "A", "Cod. cliente"
    };
    private static final float[] COL_WIDTHS = {
        22f, 8f, 8f, 8f, 8f, 8f, 4f, 4f, 5f, 8f, 8f, 9f
    };

    private PdfPTable newListinoTable() throws DocumentException {
        PdfPTable table = new PdfPTable(COL_WIDTHS.length);
        table.setWidths(COL_WIDTHS);
        table.setWidthPercentage(100f);
        table.setHeaderRows(1);
        table.setSpacingBefore(2f);
        return table;
    }

    private void addPlainHeader(PdfPTable table) {
        for (String t : COL_TITLES) {
            PdfPCell cell = headerCell(t);
            table.addCell(cell);
        }
    }

    /** Header con le soglie di scaglione effettive (sostituisce i titoli generici "Scag. N"). */
    private void addTableHeader(PdfPTable table, ListinoRow headerScaleRow) {
        table.addCell(headerCell(COL_TITLES[0]));
        for (int n = 1; n <= 5; n++) {
            String label = formatScaleHeader(headerScaleRow, n);
            table.addCell(headerCell(label.isEmpty() ? COL_TITLES[n] : label));
        }
        for (int i = 6; i < COL_TITLES.length; i++) {
            table.addCell(headerCell(COL_TITLES[i]));
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
        table.addCell(dataCell(row.getCustomerMaterialCode(), F_MATERIAL, Element.ALIGN_LEFT, bg));
    }

    private void addZoneRow(PdfPTable table, ListinoRow row) {
        Color bg = ((rowToggle++ % 2) == 0) ? Color.WHITE : COLOR_ROW_ALT_BG;
        Font font = row.isPreferredZone() ? F_ZONE_REF : F_ZONE;

        table.addCell(dataCell("   " + nvl(row.getDescription()), font, Element.ALIGN_LEFT, bg));
        for (int n = 1; n <= 5; n++) {
            String v = n > row.getActiveCols() ? "" : formatDelta(row, n);
            table.addCell(dataCell(v, font, Element.ALIGN_RIGHT, bg));
        }
        // colonne rimanenti vuote per le righe zona
        for (int i = 0; i < 6; i++) table.addCell(dataCell("", font, Element.ALIGN_CENTER, bg));
    }

    private void addFullWidthRow(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setColspan(COL_TITLES.length);
        cell.setPadding(3f);
        if (bg != null) cell.setBackgroundColor(bg);
        table.addCell(cell);
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

    private String formatScaleHeader(ListinoRow row, int n) {
        double q = row.getScaleQty()[n - 1];
        String unit = nvl(row.getScaleUnit());
        if (q <= 0) {
            if (n == 5) return unit.trim().isEmpty() ? "Qualsiasi" : "Qualsiasi (" + unit + ")";
            return "";
        }
        String qty = q == Math.floor(q)
            ? String.format("%.0f", q)
            : String.format("%.3f", q).replaceAll("0+$", "");
        return "fino a " + qty + (unit.trim().isEmpty() ? "" : " " + unit);
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
        if (row.isPreferredZone()) {
            return v == 0.0 ? "" : String.format("(%,.2f)", v);
        }
        return v != 0.0 ? String.format("%+,.2f", v) : "—";
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

                // ── Piede pagina ─────────────────────────────────────────
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
