package eOne.conditionsSD.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class ExtractParams {

    private final List<String> customers;
    private final List<String> materials;
    private final LocalDate    referenceDate;

    private ExtractParams(List<String> customers, List<String> materials, LocalDate referenceDate) {
        this.customers     = customers == null     ? Collections.emptyList() : List.copyOf(customers);
        this.materials     = materials == null     ? Collections.emptyList() : List.copyOf(materials);
        this.referenceDate = referenceDate != null ? referenceDate           : LocalDate.now();
    }

    public static ExtractParams all() {
        return new ExtractParams(null, null, LocalDate.now());
    }

    public static ExtractParams forCustomers(List<String> customers) {
        return new ExtractParams(customers, null, LocalDate.now());
    }

    public static Builder builder() { return new Builder(); }

    public List<String> getCustomers()   { return customers; }
    public List<String> getMaterials()   { return materials; }
    public LocalDate getReferenceDate()  { return referenceDate; }
    public boolean isAllCustomers()      { return customers.isEmpty(); }
    public boolean isAllMaterials()      { return materials.isEmpty(); }

    public static class Builder {
        private List<String> customers;
        private List<String> materials;
        private LocalDate    referenceDate = LocalDate.now();

        public Builder customers(List<String> v)      { this.customers = v;      return this; }
        public Builder materials(List<String> v)      { this.materials = v;      return this; }
        public Builder referenceDate(LocalDate v)     { this.referenceDate = v;  return this; }
        public ExtractParams build() { return new ExtractParams(customers, materials, referenceDate); }
    }
}
