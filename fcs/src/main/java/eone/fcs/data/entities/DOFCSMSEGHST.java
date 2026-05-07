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
@doentity(table="TABFCSMSEGHST",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="FCSMSEGHST",dataContextClassName="eone.fcs.data.datacontexts.DCFcsmseghst",controllerClassName="eone.fcs.logic.controllers.FCSMSEGHSTController",detailUIClassName="eone.fcs.view.dialogs.FCSMSEGHSTDetail",listControllerClassName="eone.fcs.logic.controllers.FCSMSEGHSTListController",beanGridUIClassName="eone.fcs.view.dialogs.FCSMSEGHSTBeanGrid")

public class DOFCSMSEGHST
    implements Serializable
{
    public static final String P_ebeln = "ebeln";
    String m_ebeln;
    @doproperty(key=true,sequence=1)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getEbeln() { return m_ebeln; }
    public void setEbeln(String value) { m_ebeln = value; }

    public static final String P_ebelp = "ebelp";
    String m_ebelp;
    @doproperty(key=true,sequence=2)
    @dovalidationinfo(charMaxLength=5)
    @XmlAttribute()
    public String getEbelp() { return m_ebelp; }
    public void setEbelp(String value) { m_ebelp = value; }

    public static final String P_etenr = "etenr";
    String m_etenr;
    @doproperty(key=true,sequence=3)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getEtenr() { return m_etenr; }
    public void setEtenr(String value) { m_etenr = value; }

    public static final String P_in_charg = "in_charg";
    String m_in_charg;
    @doproperty(key=true,sequence=5)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getIn_charg() { return m_in_charg; }
    public void setIn_charg(String value) { m_in_charg = value; }

    public static final String P_in_xblnr = "in_xblnr";
    String m_in_xblnr;
    @doproperty(key=true,sequence=4)
    @dovalidationinfo(charMaxLength=16)
    @XmlAttribute()
    public String getIn_xblnr() { return m_in_xblnr; }
    public void setIn_xblnr(String value) { m_in_xblnr = value; }

    public static final String P_mblnr = "mblnr";
    String m_mblnr;
    @doproperty(key=true,sequence=6)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getMblnr() { return m_mblnr; }
    public void setMblnr(String value) { m_mblnr = value; }

    public static final String P_mjahr = "mjahr";
    String m_mjahr;
    @doproperty(key=true,sequence=7)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getMjahr() { return m_mjahr; }
    public void setMjahr(String value) { m_mjahr = value; }

    public static final String P_bemid = "bemid";
    String m_bemid;
    @doproperty(sequence=10)
    @dovalidationinfo(charMaxLength=50,convertEmptyToNull=true)
    @XmlAttribute()
    public String getBemid() { return m_bemid; }
    public void setBemid(String value) { m_bemid = value; }

    public static final String P_charg = "charg";
    String m_charg;
    @doproperty(sequence=13)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getCharg() { return m_charg; }
    public void setCharg(String value) { m_charg = value; }

    public static final String P_datum = "datum";
    LocalDate m_datum;
    @doproperty(sequence=19)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateAdapter.class)

    public LocalDate getDatum() { return m_datum; }
    public void setDatum(LocalDate value) { m_datum = value; }

    public static final String P_ernam = "ernam";
    String m_ernam;
    @doproperty(sequence=21)
    @dovalidationinfo(charMaxLength=12)
    @XmlAttribute()
    public String getErnam() { return m_ernam; }
    public void setErnam(String value) { m_ernam = value; }

    public static final String P_id_eket = "id_eket";
    String m_id_eket;
    @doproperty(sequence=9)
    @dovalidationinfo(charMaxLength=19)
    @XmlAttribute()
    public String getId_eket() { return m_id_eket; }
    public void setId_eket(String value) { m_id_eket = value; }

    public static final String P_in_lgort = "in_lgort";
    String m_in_lgort;
    @doproperty(sequence=18)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getIn_lgort() { return m_in_lgort; }
    public void setIn_lgort(String value) { m_in_lgort = value; }

    public static final String P_in_mange = "in_mange";
    Float m_in_mange;
    @doproperty(sequence=16)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getIn_mange() { return m_in_mange; }
    public void setIn_mange(Float value) { m_in_mange = value; }

    public static final String P_in_werks = "in_werks";
    String m_in_werks;
    @doproperty(sequence=17)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getIn_werks() { return m_in_werks; }
    public void setIn_werks(String value) { m_in_werks = value; }

    public static final String P_kappl = "kappl";
    String m_kappl;
    @doproperty(sequence=8)
    @dovalidationinfo(charMaxLength=2)
    @XmlAttribute()
    public String getKappl() { return m_kappl; }
    public void setKappl(String value) { m_kappl = value; }

    public static final String P_maktx = "maktx";
    String m_maktx;
    @doproperty(sequence=14)
    @dovalidationinfo(charMaxLength=40)
    @XmlAttribute()
    public String getMaktx() { return m_maktx; }
    public void setMaktx(String value) { m_maktx = value; }

    public static final String P_matnr = "matnr";
    String m_matnr;
    @doproperty(sequence=12)
    @dovalidationinfo(charMaxLength=18)
    @XmlAttribute()
    public String getMatnr() { return m_matnr; }
    public void setMatnr(String value) { m_matnr = value; }

    public static final String P_meins = "meins";
    String m_meins;
    @doproperty(sequence=15)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getMeins() { return m_meins; }
    public void setMeins(String value) { m_meins = value; }

    public static final String P_mtart = "mtart";
    String m_mtart;
    @doproperty(sequence=11)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getMtart() { return m_mtart; }
    public void setMtart(String value) { m_mtart = value; }

    public static final String P_uzeit = "uzeit";
    LocalTime m_uzeit;
    @doproperty(sequence=20)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalTimeAdapter.class)

    public LocalTime getUzeit() { return m_uzeit; }
    public void setUzeit(LocalTime value) { m_uzeit = value; }


}