package eone.fcs.data.entities;

import java.io.Serializable;
import java.math.*;
import java.time.*;
import java.util.*;
import org.eclnt.ccee.db.dofw.annotations.*;
import org.eclnt.ccee.xml.*;
import org.eclnt.dataapp.logic.meta.*;
import jakarta.xml.bind.annotation.*;
import jakarta.xml.bind.annotation.adapters.*;



@XmlRootElement
@doentity(table="TABFCSMOVSAPHST",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="FCSMOVSAPHST",dataContextClassName="eone.fcs.data.datacontexts.DCFcsmovsaphst",controllerClassName="eone.fcs.logic.controllers.FCSMOVSAPHSTController",detailUIClassName="eone.fcs.view.dialogs.FCSMOVSAPHSTDetail",listControllerClassName="eone.fcs.logic.controllers.FCSMOVSAPHSTListController",beanGridUIClassName="eone.fcs.view.dialogs.FCSMOVSAPHSTBeanGrid")

public class DOFCSMOVSAPHST
    implements Serializable
{
    public static final String P_movid = "movid";
    String m_movid;
    @doproperty(key=true,sequence=1)
    @dovalidationinfo(charMaxLength=50,convertEmptyToNull=true)
    @XmlAttribute()
    public String getMovid() { return m_movid; }
    public void setMovid(String value) { m_movid = value; }

    public static final String P_aufnr = "aufnr";
    String m_aufnr;
    @doproperty(sequence=8)
    @dovalidationinfo(charMaxLength=12)
    @XmlAttribute()
    public String getAufnr() { return m_aufnr; }
    public void setAufnr(String value) { m_aufnr = value; }

    public static final String P_bwart = "bwart";
    String m_bwart;
    @doproperty(sequence=4)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getBwart() { return m_bwart; }
    public void setBwart(String value) { m_bwart = value; }

    public static final String P_charg = "charg";
    String m_charg;
    @doproperty(sequence=14)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getCharg() { return m_charg; }
    public void setCharg(String value) { m_charg = value; }

    public static final String P_charg_to = "charg_to";
    String m_charg_to;
    @doproperty(sequence=19)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getCharg_to() { return m_charg_to; }
    public void setCharg_to(String value) { m_charg_to = value; }

    public static final String P_datum = "datum";
    LocalDate m_datum;
    @doproperty(sequence=21)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateAdapter.class)

    public LocalDate getDatum() { return m_datum; }
    public void setDatum(LocalDate value) { m_datum = value; }

    public static final String P_kostl = "kostl";
    String m_kostl;
    @doproperty(sequence=7)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getKostl() { return m_kostl; }
    public void setKostl(String value) { m_kostl = value; }

    public static final String P_kunnr = "kunnr";
    String m_kunnr;
    @doproperty(sequence=6)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getKunnr() { return m_kunnr; }
    public void setKunnr(String value) { m_kunnr = value; }

    public static final String P_lgort = "lgort";
    String m_lgort;
    @doproperty(sequence=12)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getLgort() { return m_lgort; }
    public void setLgort(String value) { m_lgort = value; }

    public static final String P_lgort_to = "lgort_to";
    String m_lgort_to;
    @doproperty(sequence=17)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getLgort_to() { return m_lgort_to; }
    public void setLgort_to(String value) { m_lgort_to = value; }

    public static final String P_lifnr = "lifnr";
    String m_lifnr;
    @doproperty(sequence=5)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getLifnr() { return m_lifnr; }
    public void setLifnr(String value) { m_lifnr = value; }

    public static final String P_matnr = "matnr";
    String m_matnr;
    @doproperty(sequence=13)
    @dovalidationinfo(charMaxLength=18)
    @XmlAttribute()
    public String getMatnr() { return m_matnr; }
    public void setMatnr(String value) { m_matnr = value; }

    public static final String P_matnr_to = "matnr_to";
    String m_matnr_to;
    @doproperty(sequence=18)
    @dovalidationinfo(charMaxLength=18)
    @XmlAttribute()
    public String getMatnr_to() { return m_matnr_to; }
    public void setMatnr_to(String value) { m_matnr_to = value; }

    public static final String P_mblnr = "mblnr";
    String m_mblnr;
    @doproperty(key=true,sequence=2)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getMblnr() { return m_mblnr; }
    public void setMblnr(String value) { m_mblnr = value; }

    public static final String P_meins = "meins";
    String m_meins;
    @doproperty(sequence=22)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getMeins() { return m_meins; }
    public void setMeins(String value) { m_meins = value; }

    public static final String P_menge = "menge";
    Float m_menge;
    @doproperty(sequence=15)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getMenge() { return m_menge; }
    public void setMenge(Float value) { m_menge = value; }

    public static final String P_menge_to = "menge_to";
    Float m_menge_to;
    @doproperty(sequence=20)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getMenge_to() { return m_menge_to; }
    public void setMenge_to(Float value) { m_menge_to = value; }

    public static final String P_mjahr = "mjahr";
    String m_mjahr;
    @doproperty(key=true,sequence=3)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getMjahr() { return m_mjahr; }
    public void setMjahr(String value) { m_mjahr = value; }

    public static final String P_prctr = "prctr";
    String m_prctr;
    @doproperty(sequence=9)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getPrctr() { return m_prctr; }
    public void setPrctr(String value) { m_prctr = value; }

    public static final String P_sobkz = "sobkz";
    String m_sobkz;
    @doproperty(sequence=10)
    @dovalidationinfo(charMaxLength=1)
    @XmlAttribute()
    public String getSobkz() { return m_sobkz; }
    public void setSobkz(String value) { m_sobkz = value; }

    public static final String P_uname = "uname";
    String m_uname;
    @doproperty(sequence=23)
    @dovalidationinfo(charMaxLength=12)
    @XmlAttribute()
    public String getUname() { return m_uname; }
    public void setUname(String value) { m_uname = value; }

    public static final String P_uzeit = "uzeit";
    LocalTime m_uzeit;
    @doproperty(sequence=24)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalTimeAdapter.class)

    public LocalTime getUzeit() { return m_uzeit; }
    public void setUzeit(LocalTime value) { m_uzeit = value; }

    public static final String P_werks = "werks";
    String m_werks;
    @doproperty(sequence=11)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getWerks() { return m_werks; }
    public void setWerks(String value) { m_werks = value; }

    public static final String P_werks_to = "werks_to";
    String m_werks_to;
    @doproperty(sequence=16)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getWerks_to() { return m_werks_to; }
    public void setWerks_to(String value) { m_werks_to = value; }

    public static final String P_wmsst = "wmsst";
    String m_wmsst;
    @doproperty(sequence=25)
    @dovalidationinfo(charMaxLength=1)
    @XmlAttribute()
    public String getWmsst() { return m_wmsst; }
    public void setWmsst(String value) { m_wmsst = value; }


}