package lamplast.mbxa.logic.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;

/**
 * Filigrana "ANNULLATO" per i DDT annullati.
 * <p>
 * Si applica al momento, su una copia: il PDF emesso resta nel database intatto,
 * con la sua impronta SHA-256. Scritta diagonale rossa semitrasparente su ogni pagina,
 * con la data dell'annullamento sotto.
 */
public final class Filigrana {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Color ROSSO = new Color(200, 0, 0);
    private static final float OPACITA = 0.30f;
    private static final float CORPO_TITOLO = 110f;
    private static final float CORPO_DATA = 30f;

    private Filigrana() {}

    /**
     * @param pdf         PDF originale (non viene modificato)
     * @param annullatoAt data dell'annullamento; se null si scrive solo "ANNULLATO"
     * @return nuovo PDF con la filigrana su ogni pagina
     */
    public static byte[] annullato(byte[] pdf, LocalDateTime annullatoAt) throws IOException {
        if (pdf == null) return null;
        String data = annullatoAt == null ? null : "il " + annullatoAt.format(D);
        PdfReader reader = new PdfReader(pdf);
        ByteArrayOutputStream out = new ByteArrayOutputStream(pdf.length + 4096);
        try {
            PdfStamper stamper = new PdfStamper(reader, out);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(OPACITA);
            gs.setStrokeOpacity(OPACITA);

            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                Rectangle r = reader.getPageSizeWithRotation(p);
                float cx = (r.getLeft() + r.getRight()) / 2f;
                float cy = (r.getBottom() + r.getTop()) / 2f;
                // lungo la diagonale della pagina (dal basso a sinistra all'alto a destra)
                double rad = Math.atan2(r.getHeight(), r.getWidth());
                float angolo = (float) Math.toDegrees(rad);

                PdfContentByte cb = stamper.getOverContent(p);
                cb.saveState();
                cb.setGState(gs);
                cb.setColorFill(ROSSO);
                cb.beginText();
                cb.setFontAndSize(bold, CORPO_TITOLO);
                cb.showTextAligned(Element.ALIGN_CENTER, "ANNULLATO", cx, cy, angolo);
                if (data != null) {
                    // riga della data: spostata perpendicolarmente sotto la scritta principale
                    float dist = CORPO_TITOLO * 0.55f;
                    float dx = (float) (Math.sin(rad) * dist);
                    float dy = (float) (-Math.cos(rad) * dist);
                    cb.setFontAndSize(bold, CORPO_DATA);
                    cb.showTextAligned(Element.ALIGN_CENTER, data, cx + dx, cy + dy, angolo);
                }
                cb.endText();
                cb.restoreState();
            }
            stamper.close();
        } catch (DocumentException e) {
            throw new IOException("Filigrana non applicabile: " + e.getMessage(), e);
        } finally {
            reader.close();
        }
        return out.toByteArray();
    }
}
