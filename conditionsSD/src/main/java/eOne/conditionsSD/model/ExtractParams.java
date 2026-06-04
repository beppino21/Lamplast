package eOne.conditionsSD.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class ExtractParams {

    private final List<String> customers;
    private final List<String> materials;
    private final LocalDate    referenceDate;
    private final String       priceGroup;
    private final ExtractMode  extractMode;

    private ExtractParams(List<String> customers, List<String> materials,
                          LocalDate referenceDate, String priceGroup,
                          ExtractMode extractMode) {
        this.customers     = customers == null     ? Collections.emptyList() : List.copyOf(customers);
        this.materials     = materials == null     ? Collections.emptyList() : List.copyOf(materials);
        this.referenceDate = referenceDate != null ? referenceDate           : LocalDate.now();
        this.priceGroup    = priceGroup != null && !priceGroup.isBlank() ? priceGroup.strip() : null;
        this.extractMode   = extractMode != null ? extractMode : ExtractMode.FULL;
    }

    public static Builder builder() { return new Builder(); }

    public List<String> getCustomers()     { return customers; }
    public List<String> getMaterials()     { return materials; }
    public LocalDate    getReferenceDate() { return referenceDate; }
    public String       getPriceGroup()    { return priceGroup; }
    public ExtractMode  getExtractMode()   { return extractMode; }
    public boolean      isAllCustomers()   { return customers.isEmpty() && priceGroup == null; }
    public boolean      isAllMaterials()   { return materials.isEmpty(); }
    public boolean      hasPriceGroup()    { return priceGroup != null; }

    public static class Builder {
        private List<String> customers;
        private List<String> materials;
        private LocalDate    referenceDate = LocalDate.now();
        private String       priceGroup;
        private ExtractMode  extractMode   = ExtractMode.FULL;

        public Builder customers(List<String> v)    { this.customers    = v; return this; }
        public Builder materials(List<String> v)    { this.materials    = v; return this; }
        public Builder referenceDate(LocalDate v)   { this.referenceDate = v; return this; }
        public Builder priceGroup(String v)         { this.priceGroup   = v; return this; }
        public Builder extractMode(ExtractMode v)   { this.extractMode  = v; return this; }
        public ExtractParams build() {
            return new ExtractParams(customers, materials, referenceDate, priceGroup, extractMode);
        }
    }
}
