package lamplast.mbxa.logic.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import lamplast.mbxa.logic.config.AppConfig;
import lamplast.mbxa.logic.model.Ddt;
import lamplast.mbxa.logic.model.DdtPosizione;

/**
 * PDF del DDT, ricalcato sul modulo stampato da R/3 con la MBXA:
 * intestazione aziendale, destinatario, riquadro "Documento di trasporto",
 * griglia dei dati di trasporto e tabella delle posizioni (intestazione ripetuta
 * sulle pagine successive).
 */
public class DdtPdfBuilder {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter H = DateTimeFormatter.ofPattern("HH:mm");
    private static final String LOGO_RESOURCE = "/lamplast/mbxa/pdf/logo.jpg";

    private static final Color GRIGIO = new Color(0x55, 0x55, 0x55);
    private static final float BORDO = 0.6f;

    private static final Font F_LABEL = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, GRIGIO);
    private static final Font F_VALUE = new Font(Font.HELVETICA, 9.5f, Font.NORMAL);
    private static final Font F_VALUE_B = new Font(Font.HELVETICA, 9.5f, Font.BOLD);
    private static final Font F_SMALL = new Font(Font.HELVETICA, 8.5f, Font.NORMAL);
    /** Diciture sotto il logo: corsivo serif, come sul modulo originale. */
    private static final Font F_SOTTO_LOGO = new Font(Font.TIMES_ROMAN, 10.5f, Font.ITALIC);
    private static final Font F_TITLE = new Font(Font.HELVETICA, 10.5f, Font.BOLD);
    private static final Font F_TITLE_S = new Font(Font.HELVETICA, 7f, Font.BOLD);
    private static final Font F_HEAD = new Font(Font.HELVETICA, 8f, Font.BOLD);
    private static final Font F_BIG = new Font(Font.HELVETICA, 16f, Font.BOLD);
    private static final Font F_FOOT = new Font(Font.HELVETICA, 7f, Font.NORMAL, GRIGIO);

    private final AppConfig m_cfg;
    private final Map<String, String> m_alias;

    public DdtPdfBuilder(AppConfig cfg) {
        m_cfg = cfg;
        m_alias = cfg.getAliasUnitaMisura();
    }

    public byte[] build(Ddt d) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 30, 40);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new PiePagina(d));
        doc.addTitle("DDT " + nvl(d.getDdtNumero()) + " - documento materiale " + d.getMatDocChiave());
        doc.addCreator("MBXA - Lamplast");
        doc.open();

        doc.add(intestazione());
        doc.add(spazio(8));
        doc.add(destinatarioERiquadro(d));
        doc.add(spazio(10));
        doc.add(rigaDocumento(d));
        doc.add(rigaTrasporto(d));
        doc.add(rigaVettore(d));
        doc.add(rigaAnnotazioni(d));
        doc.add(spazio(8));
        doc.add(posizioni(d));

        doc.close();
        return out.toByteArray();
    }

    // ------------------------------------------------------------------------
    // blocchi
    // ------------------------------------------------------------------------

    private PdfPTable intestazione() throws Exception {
        PdfPTable t = new PdfPTable(new float[] { 55, 45 });
        t.setWidthPercentage(100);

        PdfPCell sx = new PdfPCell();
        sx.setBorder(Rectangle.NO_BORDER);
        sx.setPadding(0);
        Image logo = logo();
        List<String> sottoLogo = new java.util.ArrayList<>(m_cfg.getRigheSottoLogo());
        if (logo != null) {
            logo.scaleToFit(205, 57);
            sx.addElement(logo);
        } else if (!sottoLogo.isEmpty()) {
            // senza logo la prima riga (ragione sociale) diventa il titolo
            sx.addElement(new Paragraph(sottoLogo.remove(0), F_BIG));
        }
        boolean prima = true;
        for (String s : sottoLogo) {
            Paragraph p = new Paragraph(s, F_SOTTO_LOGO);
            p.setLeading(12.5f);
            if (prima && logo != null) p.setSpacingBefore(6);
            prima = false;
            sx.addElement(p);
        }
        t.addCell(sx);

        PdfPCell dx = new PdfPCell();
        dx.setBorder(Rectangle.NO_BORDER);
        dx.setPaddingTop(14);
        for (String s : m_cfg.getRigheAzienda()) {
            Paragraph p = new Paragraph(s, F_SMALL);
            p.setLeading(10.5f);
            dx.addElement(p);
        }
        t.addCell(dx);
        return t;
    }

    private PdfPTable destinatarioERiquadro(Ddt d) {
        PdfPTable t = new PdfPTable(new float[] { 55, 45 });
        t.setWidthPercentage(100);

        PdfPCell sx = new PdfPCell();
        sx.setBorder(Rectangle.NO_BORDER);
        sx.setPaddingLeft(0);
        sx.addElement(par("Destinatario merci", F_VALUE_B, 12));
        sx.addElement(par("Cod. " + nvl(d.getDestCodice()), F_VALUE, 12));
        sx.addElement(par(nvl(d.getDestRagioneSociale()), F_VALUE, 12));
        sx.addElement(par(nvl(d.getDestIndirizzo()), F_VALUE, 12));
        sx.addElement(par(localita(d.getDestCap(), d.getDestCitta(), d.getDestProvincia(), d.getDestNazione()), F_VALUE, 12));
        sx.addElement(spazio(6));
        Paragraph piva = new Paragraph();
        piva.setLeading(12);
        piva.add(new Chunk("Partita Iva/Codice Fiscale: ", F_VALUE_B));
        piva.add(new Chunk(nvl(d.getDestPartitaIva()), F_VALUE));
        sx.addElement(piva);
        t.addCell(sx);

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell title = new PdfPCell();
        title.setBorderWidth(BORDO);
        title.setPadding(4);
        Paragraph tp = new Paragraph();
        tp.add(new Chunk("Documento di trasporto ", F_TITLE));
        tp.add(new Chunk("(D.P.R. 472 del 14/08/96)", F_TITLE_S));
        title.addElement(tp);
        box.addCell(title);

        PdfPCell body = new PdfPCell();
        body.setBorderWidth(BORDO);
        body.setPadding(4);
        body.setPaddingBottom(8);
        body.addElement(par("Numero: " + nvl(d.getMatDoc()), F_VALUE, 12.5f));
        body.addElement(par("Data Reg.: " + data(d.getDataRegistrazione()), F_VALUE, 12.5f));
        Paragraph ddt = new Paragraph();
        ddt.setLeading(12.5f);
        ddt.add(new Chunk("DDT: ", F_VALUE));
        ddt.add(new Chunk(nvl(d.getDdtNumero()), F_VALUE_B));
        body.addElement(ddt);
        body.addElement(par("Partenza: Cod. " + nvl(d.getMittCodice()) + " " + nvl(d.getMittRagioneSociale()), F_VALUE, 12.5f));
        body.addElement(par(unisci(nvl(d.getMittIndirizzo()),
            localita(d.getMittCap(), d.getMittCitta(), d.getMittProvincia(), d.getMittNazione())), F_SMALL, 11));
        box.addCell(body);

        PdfPCell dx = new PdfPCell(box);
        dx.setBorder(Rectangle.NO_BORDER);
        dx.setPadding(0);
        t.addCell(dx);
        return t;
    }

    private PdfPTable rigaDocumento(Ddt d) {
        PdfPTable t = griglia(new float[] { 30, 20, 20, 30 });
        t.addCell(campo("TIPO DOCUMENTO", "DOCUMENTO DI TRASPORTO"));
        t.addCell(campo("NUMERO", nvl(d.getDdtNumero())));
        t.addCell(campo("DATA", data(d.getDdtData())));
        t.addCell(campo("Doc. Materiale SAP", nvl(d.getMatDoc())));
        return t;
    }

    private PdfPTable rigaTrasporto(Ddt d) {
        PdfPTable t = griglia(new float[] { 22, 18, 13, 13, 9, 25 });
        t.addCell(campo("CAUSALE DEL TRASPORTO", nvl(d.getCausale())));
        t.addCell(campo("ASPETTO DEI BENI", nvl(d.getAspettoBeni())));
        String um = d.getUnitaPeso() == null ? "KG" : d.getUnitaPeso();
        t.addCell(campo("PESO LORDO " + um, numero(d.getPesoLordo()), Element.ALIGN_RIGHT));
        t.addCell(campo("PESO NETTO " + um, numero(d.getPesoNetto()), Element.ALIGN_RIGHT));
        t.addCell(campo("N° COLLI", d.getColli() == null ? "" : String.valueOf(d.getColli()), Element.ALIGN_RIGHT));
        t.addCell(campo("PORTO", nvl(d.getPorto())));
        return t;
    }

    private PdfPTable rigaVettore(Ddt d) {
        PdfPTable t = griglia(new float[] { 48, 13, 10, 29 });
        String vettore;
        if (d.getVettRagioneSociale() == null || d.getVettRagioneSociale().isBlank()) {
            vettore = "Mittente";
        } else {
            vettore = d.getVettRagioneSociale();
            if (d.getVettIndirizzo() != null && !d.getVettIndirizzo().isBlank()) vettore += "\n" + d.getVettIndirizzo();
            String extra = "";
            if (d.getVettPartitaIva() != null && !d.getVettPartitaIva().isBlank()) extra += "P.IVA " + d.getVettPartitaIva();
            if (d.getVettIscrizioneAlbo() != null && !d.getVettIscrizioneAlbo().isBlank()) {
                extra += (extra.isEmpty() ? "" : " - ") + "Albo " + d.getVettIscrizioneAlbo();
            }
            if (!extra.isEmpty()) vettore += "\n" + extra;
        }
        t.addCell(campo("TRASPORTO A CURA DEL", vettore));
        t.addCell(campo("DATA", d.getInizioTrasporto() == null ? "" : d.getInizioTrasporto().format(D)));
        t.addCell(campo("ORA", d.getInizioTrasporto() == null ? "" : d.getInizioTrasporto().format(H)));
        PdfPCell firma = campo("FIRMA VETTORE", "");
        firma.setMinimumHeight(34);
        t.addCell(firma);
        return t;
    }

    private PdfPTable rigaAnnotazioni(Ddt d) {
        PdfPTable t = griglia(new float[] { 62, 38 });
        PdfPCell note = campo("ANNOTAZIONI E VARIAZIONI", nvl(d.getAnnotazioni()));
        note.setMinimumHeight(34);
        t.addCell(note);
        t.addCell(campo("FIRMA DESTINATARIO", ""));
        return t;
    }

    private PdfPTable posizioni(Ddt d) {
        PdfPTable t = new PdfPTable(new float[] { 74, 10, 16 });
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.addCell(intestazioneColonna("Codice del Materiale/Descrizione", Element.ALIGN_LEFT));
        t.addCell(intestazioneColonna("UM", Element.ALIGN_LEFT));
        t.addCell(intestazioneColonna("Q.tà", Element.ALIGN_RIGHT));

        for (DdtPosizione p : d.getPosizioni()) {
            PdfPCell c = cellaPos();
            Paragraph codice = new Paragraph();
            codice.setLeading(11.5f);
            codice.add(new Chunk("Codice: ", F_SMALL));
            codice.add(new Chunk(nvl(p.getMateriale()), F_VALUE_B));
            c.addElement(codice);
            Paragraph descr = par(nvl(p.getDescrizione()), F_VALUE, 11.5f);
            descr.setIndentationLeft(18);
            c.addElement(descr);
            if (p.getLotto() != null && !p.getLotto().isBlank()) {
                Paragraph lotto = par("Lotto: " + p.getLotto(), F_SMALL, 10.5f);
                lotto.setIndentationLeft(18);
                c.addElement(lotto);
            }
            t.addCell(c);

            PdfPCell um = cellaPos();
            um.addElement(par(" ", F_SMALL, 11.5f));
            um.addElement(par(unita(p.getUm()), F_VALUE, 11.5f));
            t.addCell(um);

            PdfPCell q = cellaPos();
            q.addElement(par(" ", F_SMALL, 11.5f));
            Paragraph qp = par(numero(p.getQuantita()), F_VALUE, 11.5f);
            qp.setAlignment(Element.ALIGN_RIGHT);
            q.addElement(qp);
            t.addCell(q);
        }
        return t;
    }

    // ------------------------------------------------------------------------
    // celle
    // ------------------------------------------------------------------------

    private static PdfPTable griglia(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        return t;
    }

    private static PdfPCell campo(String label, String value) {
        return campo(label, value, Element.ALIGN_LEFT);
    }

    /** Cella della griglia: etichetta piccola in alto, valore sotto (come il modulo R/3). */
    private static PdfPCell campo(String label, String value, int align) {
        PdfPCell c = new PdfPCell();
        c.setBorderWidth(BORDO);
        c.setPadding(3);
        c.setPaddingBottom(5);
        c.setMinimumHeight(28);
        Paragraph l = new Paragraph(label, F_LABEL);
        l.setLeading(8);
        c.addElement(l);
        Paragraph v = new Paragraph(value == null ? "" : value, F_VALUE);
        v.setLeading(11.5f);
        v.setAlignment(align);
        c.addElement(v);
        return c;
    }

    private static PdfPCell intestazioneColonna(String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, F_HEAD));
        c.setBorderWidth(BORDO);
        c.setBackgroundColor(new Color(0xEE, 0xEE, 0xEE));
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        return c;
    }

    private static PdfPCell cellaPos() {
        PdfPCell c = new PdfPCell();
        c.setBorderWidth(BORDO);
        c.setPadding(3);
        c.setPaddingBottom(5);
        return c;
    }

    private static Paragraph par(String s, Font f, float leading) {
        Paragraph p = new Paragraph(s, f);
        p.setLeading(leading);
        return p;
    }

    private static Paragraph spazio(float h) {
        Paragraph p = new Paragraph(" ", F_SMALL);
        p.setLeading(h);
        return p;
    }

    // ------------------------------------------------------------------------
    // piè di pagina
    // ------------------------------------------------------------------------

    private static class PiePagina extends PdfPageEventHelper {
        private final String m_rif;

        PiePagina(Ddt d) {
            m_rif = "DDT " + nvl(d.getDdtNumero()) + " del " + data(d.getDdtData())
                + " - Doc. materiale " + nvl(d.getMatDoc());
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                new Phrase(m_rif, F_FOOT), document.left(), document.bottom() - 18, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                new Phrase("Pag. " + writer.getPageNumber(), F_FOOT), document.right(), document.bottom() - 18, 0);
        }
    }

    // ------------------------------------------------------------------------
    // formattazione
    // ------------------------------------------------------------------------

    private Image logo() {
        try {
            String path = m_cfg.getLogoPath();
            if (path != null) {
                if (Files.isRegularFile(Paths.get(path))) return Image.getInstance(Files.readAllBytes(Paths.get(path)));
                System.err.println("[MBXA] Logo: file indicato in pdf.logo non trovato: " + Paths.get(path).toAbsolutePath());
            }
            try (InputStream in = DdtPdfBuilder.class.getResourceAsStream(LOGO_RESOURCE)) {
                if (in != null) return Image.getInstance(in.readAllBytes());
            }
            System.err.println("[MBXA] Logo: risorsa " + LOGO_RESOURCE + " assente nel classpath"
                + " (WEB-INF/classes) e pdf.logo non impostato: stampo senza logo.");
        } catch (Exception e) {
            System.err.println("[MBXA] Logo non caricato: " + e.getMessage());
        }
        return null;
    }

    private String unita(String um) {
        if (um == null) return "";
        String a = m_alias.get(um.trim().toUpperCase());
        return a != null ? a : um;
    }

    /** Numero all'italiana: separatore delle migliaia, fino a 3 decimali, senza zeri inutili. */
    public static String numero(BigDecimal v) {
        if (v == null) return "";
        DecimalFormat f = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.ITALY));
        return f.format(v);
    }

    private static String data(java.time.LocalDate d) {
        return d == null ? "" : d.format(D);
    }

    static String localita(String cap, String citta, String prov, String nazione) {
        StringBuilder sb = new StringBuilder();
        if (cap != null && !cap.isBlank()) sb.append(cap.trim()).append(' ');
        if (citta != null && !citta.isBlank()) sb.append(citta.trim());
        if (prov != null && !prov.isBlank()) sb.append(" (").append(prov.trim()).append(')');
        if (nazione != null && !nazione.isBlank() && !"IT".equalsIgnoreCase(nazione.trim())) {
            sb.append(" - ").append(nazione.trim());
        }
        return sb.toString().trim();
    }

    private static String unisci(String a, String b) {
        if (a.isBlank()) return b;
        if (b.isBlank()) return a;
        return a + " - " + b;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}