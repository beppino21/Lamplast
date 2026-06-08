package it.eone.coge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Costruisce il payload SOAP per:
 *   Journal Entry - Post (Synchronous)
 *   SAP_COM_0002 / JournalEntryCreateRequestConfirmation_In
 *
 * Struttura ricavata dal WSDL ufficiale del tenant (JOURNALENTRYCREATEREQUESTCONFI.wsdl):
 *
 *   - Gli importi sono elementi complessi con attributo currencyCode:
 *       <AmountInTransactionCurrency currencyCode="EUR">9154.88</AmountInTransactionCurrency>
 *
 *   - Il campo cliente nel DebtorItem si chiama <Debtor> (non <Customer>)
 *
 *   - Il Co.Ge. Speciale va in <DownPaymentTerms><SpecialGLCode>
 *
 *   - Il DebtorItem richiede <ReferenceDocumentItem> (numero posizione, es. "1")
 *
 *   - PaymentMethod e BaselineDate stanno dentro <PaymentDetails>
 *
 *   - Il messaggio esterno e' JournalEntryBulkCreateRequest (non JournalEntryCreateRequest)
 *     che contiene uno o piu' JournalEntryCreateRequest
 */
public class SoapBuilder {

    private static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String NS_SFIN = "http://sap.com/xi/SAPSCORE/SFIN";

    private static final DateTimeFormatter DATE_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AppConfig config;
    private final String    effectivePostingDate;

    public SoapBuilder(AppConfig config) {
        this.config = config;
        this.effectivePostingDate = resolvePostingDate(config.postingDate);
    }

    private static String resolvePostingDate(String configured) {
        if (configured == null || configured.isBlank()) {
            return LocalDate.now().format(DATE_ISO);
        }
        return configured;
    }

    public String buildSyncEnvelope(CsvRow row, String msgId) {
        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<soapenv:Envelope");
        sb.append(" xmlns:soapenv=\"").append(NS_SOAP).append("\"");
        sb.append(" xmlns:sfin=\"").append(NS_SFIN).append("\">\n");
        sb.append("  <soapenv:Header/>\n");
        sb.append("  <soapenv:Body>\n");

        // Il messaggio esterno e' JournalEntryBulkCreateRequest
        sb.append("    <sfin:JournalEntryBulkCreateRequest>\n");

        // --- MessageHeader esterno ---
        sb.append("      <MessageHeader>\n");
        sb.append("        <ID>").append(escXml(msgId)).append("</ID>\n");
        sb.append("        <CreationDateTime>").append(nowDateTime()).append("</CreationDateTime>\n");
        sb.append("      </MessageHeader>\n");

        // --- JournalEntryCreateRequest (uno per documento) ---
        sb.append("      <JournalEntryCreateRequest>\n");

        // MessageHeader interno
        sb.append("        <MessageHeader>\n");
        sb.append("          <ID>").append(escXml(msgId)).append("-1</ID>\n");
        sb.append("          <CreationDateTime>").append(nowDateTime()).append("</CreationDateTime>\n");
        sb.append("        </MessageHeader>\n");

        // JournalEntry
        sb.append("        <JournalEntry>\n");

        // Testata
        sb.append("          <OriginalReferenceDocumentType>BKPFF</OriginalReferenceDocumentType>\n");
        sb.append("          <BusinessTransactionType>RFBU</BusinessTransactionType>\n");
        sb.append("          <AccountingDocumentType>")
          .append(escXml(config.documentType))
          .append("</AccountingDocumentType>\n");
        sb.append("          <CompanyCode>")
          .append(escXml(config.companyCode))
          .append("</CompanyCode>\n");
        sb.append("          <DocumentDate>")
          .append(escXml(row.bldatIso.isEmpty() ? effectivePostingDate : row.bldatIso))
          .append("</DocumentDate>\n");
        sb.append("          <PostingDate>")
          .append(escXml(effectivePostingDate))
          .append("</PostingDate>\n");
        sb.append("          <DocumentReferenceID>")
          .append(escXml(cleanXblnr(row.xblnr)))
          .append("</DocumentReferenceID>\n");
        sb.append("          <DocumentHeaderText>")
          .append(escXml(truncate("MIGR.CGS " + cleanXblnr(row.xblnr), 25)))
          .append("</DocumentHeaderText>\n");
        sb.append("          <CreatedByUser>")
          .append(escXml(config.createdByUser))
          .append("</CreatedByUser>\n");

        // POS 1: DebtorItem — DARE Cliente (partita aperta ordinaria)
        // Nota: migrazione come PA ordinaria; il Co.Ge. Speciale (UMSKZ)
        // verra' gestito manualmente in S/4HC dopo la migrazione.
        sb.append("          <DebtorItem>\n");
        sb.append("            <ReferenceDocumentItem>1</ReferenceDocumentItem>\n");
        sb.append("            <Debtor>")
          .append(escXml(row.kunnr.trim()))
          .append("</Debtor>\n");
        // Importo con attributo currencyCode
        sb.append("            <AmountInTransactionCurrency currencyCode=\"")
          .append(escXml(row.waers)).append("\">")
          .append(formatAmount(row.wrbtr))
          .append("</AmountInTransactionCurrency>\n");
        // Importo divisa societa' (solo se presente e diverso da zero)
        if (row.dmbtr.compareTo(BigDecimal.ZERO) != 0) {
            sb.append("            <AmountInCompanyCodeCurrency currencyCode=\"")
              .append(escXml(config.companyCurrency)).append("\">")
              .append(formatAmount(row.dmbtr))
              .append("</AmountInCompanyCodeCurrency>\n");
        }
        // Testo posizione
        if (!row.sgtxt.isEmpty()) {
            sb.append("            <DocumentItemText>")
              .append(escXml(truncate(row.sgtxt, 50)))
              .append("</DocumentItemText>\n");
        }
        // Numero attribuzione (ZUONR)
        if (!row.zuonr.isEmpty()) {
            sb.append("            <AssignmentReference>")
              .append(escXml(truncate(row.zuonr, 18)))
              .append("</AssignmentReference>\n");
        }
        // Condizioni pagamento
        if (!row.zterm.isEmpty()) {
            sb.append("            <CashDiscountTerms>\n");
            sb.append("              <DueCalculationBaseDate>")
              .append(escXml(row.zfbdtIso.isEmpty() ? effectivePostingDate : row.zfbdtIso))
              .append("</DueCalculationBaseDate>\n");
            sb.append("              <NetPaymentDays>P0D</NetPaymentDays>\n");
            sb.append("            </CashDiscountTerms>\n");
        }
        // Dettagli pagamento (PaymentMethod)
        if (!row.zlsch.isEmpty()) {
            sb.append("            <PaymentDetails>\n");
            sb.append("              <PaymentMethod>")
              .append(escXml(row.zlsch))
              .append("</PaymentMethod>\n");
            sb.append("            </PaymentDetails>\n");
        }
        sb.append("          </DebtorItem>\n");

        // POS 2: Item — AVERE conto transitorio (importo negativo)
        sb.append("          <Item>\n");
        sb.append("            <GLAccount>")
          .append(escXml(config.transitoryAccount.trim()))
          .append("</GLAccount>\n");
        sb.append("            <AmountInTransactionCurrency currencyCode=\"")
          .append(escXml(row.waers)).append("\">")
          .append(formatAmount(row.wrbtr.negate()))
          .append("</AmountInTransactionCurrency>\n");
        if (row.dmbtr.compareTo(BigDecimal.ZERO) != 0) {
            sb.append("            <AmountInCompanyCodeCurrency currencyCode=\"")
              .append(escXml(config.companyCurrency)).append("\">")
              .append(formatAmount(row.dmbtr.negate()))
              .append("</AmountInCompanyCodeCurrency>\n");
        }
        sb.append("            <DocumentItemText>")
          .append(escXml(truncate("MIGR.CGS " + cleanXblnr(row.xblnr), 50)))
          .append("</DocumentItemText>\n");
        sb.append("          </Item>\n");

        sb.append("        </JournalEntry>\n");
        sb.append("      </JournalEntryCreateRequest>\n");
        sb.append("    </sfin:JournalEntryBulkCreateRequest>\n");
        sb.append("  </soapenv:Body>\n");
        sb.append("</soapenv:Envelope>");

        return sb.toString();
    }

    private static String cleanXblnr(String xblnr) {
        if (xblnr == null) return "";
        String s = xblnr.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.length() > 16 ? s.substring(s.length() - 16) : s;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String nowDateTime() {
        return java.time.Instant.now().toString();
    }

    private static String escXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
