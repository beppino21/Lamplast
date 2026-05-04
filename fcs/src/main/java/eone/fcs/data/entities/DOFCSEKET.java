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
@doentity(table="TABFCSEKET",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="FCSEKET",dataContextClassName="eone.fcs.data.datacontexts.DCFcseket",controllerClassName="eone.fcs.logic.controllers.FCSEKETController",detailUIClassName="eone.fcs.view.dialogs.FCSEKETDetail",listControllerClassName="eone.fcs.logic.controllers.FCSEKETListController",beanGridUIClassName="eone.fcs.view.dialogs.FCSEKETBeanGrid")

public class DOFCSEKET
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

    public static final String P_ameng = "ameng";
    Float m_ameng;
    @doproperty(sequence=18)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getAmeng() { return m_ameng; }
    public void setAmeng(Float value) { m_ameng = value; }

    public static final String P_bemid = "bemid";
    UUID m_bemid = UUID.randomUUID();
    @doproperty(sequence=36,uuid2string=true)
    @dovalidationinfo(charMaxLength=36)
    @XmlAttribute()
    public UUID getBemid() { return m_bemid; }
    public void setBemid(UUID value) { m_bemid = value; }

    public static final String P_brgew_row = "brgew_row";
    Float m_brgew_row;
    @doproperty(sequence=31)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getBrgew_row() { return m_brgew_row; }
    public void setBrgew_row(Float value) { m_brgew_row = value; }

    public static final String P_bstme = "bstme";
    String m_bstme;
    @doproperty(sequence=23)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getBstme() { return m_bstme; }
    public void setBstme(String value) { m_bstme = value; }

    public static final String P_bstmexpallet = "bstmexpallet";
    Integer m_bstmexpallet;
    @doproperty(sequence=26)
    @dovalidationinfo(numericPrecision=10)
    @XmlAttribute()
    public Integer getBstmexpallet() { return m_bstmexpallet; }
    public void setBstmexpallet(Integer value) { m_bstmexpallet = value; }

    public static final String P_charg = "charg";
    String m_charg;
    @doproperty(sequence=13)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getCharg() { return m_charg; }
    public void setCharg(String value) { m_charg = value; }

    public static final String P_datum = "datum";
    LocalDate m_datum;
    @doproperty(sequence=33)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateAdapter.class)

    public LocalDate getDatum() { return m_datum; }
    public void setDatum(LocalDate value) { m_datum = value; }

    public static final String P_eindt = "eindt";
    LocalDate m_eindt;
    @doproperty(sequence=7)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateAdapter.class)

    public LocalDate getEindt() { return m_eindt; }
    public void setEindt(LocalDate value) { m_eindt = value; }

    public static final String P_ernam = "ernam";
    String m_ernam;
    @doproperty(sequence=35)
    @dovalidationinfo(charMaxLength=12)
    @XmlAttribute()
    public String getErnam() { return m_ernam; }
    public void setErnam(String value) { m_ernam = value; }

    public static final String P_gewei = "gewei";
    String m_gewei;
    @doproperty(sequence=37)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getGewei() { return m_gewei; }
    public void setGewei(String value) { m_gewei = value; }

    public static final String P_id_eket = "id_eket";
    String m_id_eket;
    @doproperty(sequence=5)
    @dovalidationinfo(charMaxLength=19)
    @XmlAttribute()
    public String getId_eket() { return m_id_eket; }
    public void setId_eket(String value) { m_id_eket = value; }

    public static final String P_in_bldat = "in_bldat";
    LocalDate m_in_bldat;
    @doproperty(sequence=40)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateAdapter.class)

    public LocalDate getIn_bldat() { return m_in_bldat; }
    public void setIn_bldat(LocalDate value) { m_in_bldat = value; }

    public static final String P_in_brgew_row = "in_brgew_row";
    Float m_in_brgew_row;
    @doproperty(sequence=50)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getIn_brgew_row() { return m_in_brgew_row; }
    public void setIn_brgew_row(Float value) { m_in_brgew_row = value; }

    public static final String P_in_brgew_tot = "in_brgew_tot";
    Float m_in_brgew_tot;
    @doproperty(sequence=43)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getIn_brgew_tot() { return m_in_brgew_tot; }
    public void setIn_brgew_tot(Float value) { m_in_brgew_tot = value; }

    public static final String P_in_charg = "in_charg";
    String m_in_charg;
    @doproperty(sequence=45)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getIn_charg() { return m_in_charg; }
    public void setIn_charg(String value) { m_in_charg = value; }

    public static final String P_in_colli_row = "in_colli_row";
    Integer m_in_colli_row;
    @doproperty(sequence=49)
    @dovalidationinfo(numericPrecision=5)
    @XmlAttribute()
    public Integer getIn_colli_row() { return m_in_colli_row; }
    public void setIn_colli_row(Integer value) { m_in_colli_row = value; }

    public static final String P_in_colli_tot = "in_colli_tot";
    Integer m_in_colli_tot;
    @doproperty(sequence=42)
    @dovalidationinfo(numericPrecision=5)
    @XmlAttribute()
    public Integer getIn_colli_tot() { return m_in_colli_tot; }
    public void setIn_colli_tot(Integer value) { m_in_colli_tot = value; }

    public static final String P_in_data_arrivo = "in_data_arrivo";
    LocalDate m_in_data_arrivo;
    @doproperty(sequence=53)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateAdapter.class)

    public LocalDate getIn_data_arrivo() { return m_in_data_arrivo; }
    public void setIn_data_arrivo(LocalDate value) { m_in_data_arrivo = value; }

    public static final String P_in_lgort = "in_lgort";
    String m_in_lgort;
    @doproperty(sequence=48)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getIn_lgort() { return m_in_lgort; }
    public void setIn_lgort(String value) { m_in_lgort = value; }

    public static final String P_in_menge = "in_menge";
    Float m_in_menge;
    @doproperty(sequence=46)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getIn_menge() { return m_in_menge; }
    public void setIn_menge(Float value) { m_in_menge = value; }

    public static final String P_in_ntgew_row = "in_ntgew_row";
    Float m_in_ntgew_row;
    @doproperty(sequence=51)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getIn_ntgew_row() { return m_in_ntgew_row; }
    public void setIn_ntgew_row(Float value) { m_in_ntgew_row = value; }

    public static final String P_in_ntgew_tot = "in_ntgew_tot";
    Float m_in_ntgew_tot;
    @doproperty(sequence=44)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getIn_ntgew_tot() { return m_in_ntgew_tot; }
    public void setIn_ntgew_tot(Float value) { m_in_ntgew_tot = value; }

    public static final String P_in_qtaxtag = "in_qtaxtag";
    Float m_in_qtaxtag;
    @doproperty(sequence=52)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getIn_qtaxtag() { return m_in_qtaxtag; }
    public void setIn_qtaxtag(Float value) { m_in_qtaxtag = value; }

    public static final String P_in_traid = "in_traid";
    String m_in_traid;
    @doproperty(sequence=41)
    @dovalidationinfo(charMaxLength=20)
    @XmlAttribute()
    public String getIn_traid() { return m_in_traid; }
    public void setIn_traid(String value) { m_in_traid = value; }

    public static final String P_in_werks = "in_werks";
    String m_in_werks;
    @doproperty(sequence=47)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getIn_werks() { return m_in_werks; }
    public void setIn_werks(String value) { m_in_werks = value; }

    public static final String P_in_xblnr = "in_xblnr";
    String m_in_xblnr;
    @doproperty(sequence=39)
    @dovalidationinfo(charMaxLength=16)
    @XmlAttribute()
    public String getIn_xblnr() { return m_in_xblnr; }
    public void setIn_xblnr(String value) { m_in_xblnr = value; }

    public static final String P_kappl = "kappl";
    String m_kappl;
    @doproperty(sequence=4)
    @dovalidationinfo(charMaxLength=2)
    @XmlAttribute()
    public String getKappl() { return m_kappl; }
    public void setKappl(String value) { m_kappl = value; }

    public static final String P_lgort = "lgort";
    String m_lgort;
    @doproperty(sequence=16)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getLgort() { return m_lgort; }
    public void setLgort(String value) { m_lgort = value; }

    public static final String P_lifnr = "lifnr";
    String m_lifnr;
    @doproperty(sequence=9)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getLifnr() { return m_lifnr; }
    public void setLifnr(String value) { m_lifnr = value; }

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
    @doproperty(sequence=22)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getMeins() { return m_meins; }
    public void setMeins(String value) { m_meins = value; }

    public static final String P_menge = "menge";
    Float m_menge;
    @doproperty(sequence=17)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getMenge() { return m_menge; }
    public void setMenge(Float value) { m_menge = value; }

    public static final String P_menge_open = "menge_open";
    Float m_menge_open;
    @doproperty(sequence=21)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getMenge_open() { return m_menge_open; }
    public void setMenge_open(Float value) { m_menge_open = value; }

    public static final String P_mengexbstme = "mengexbstme";
    Float m_mengexbstme;
    @doproperty(sequence=24)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getMengexbstme() { return m_mengexbstme; }
    public void setMengexbstme(Float value) { m_mengexbstme = value; }

    public static final String P_mtart = "mtart";
    String m_mtart;
    @doproperty(sequence=11)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getMtart() { return m_mtart; }
    public void setMtart(String value) { m_mtart = value; }

    public static final String P_name1 = "name1";
    String m_name1;
    @doproperty(sequence=10)
    @dovalidationinfo(charMaxLength=80)
    @XmlAttribute()
    public String getName1() { return m_name1; }
    public void setName1(String value) { m_name1 = value; }

    public static final String P_nrbag = "nrbag";
    Integer m_nrbag;
    @doproperty(sequence=30)
    @dovalidationinfo(numericPrecision=10)
    @XmlAttribute()
    public Integer getNrbag() { return m_nrbag; }
    public void setNrbag(Integer value) { m_nrbag = value; }

    public static final String P_nrtag = "nrtag";
    Integer m_nrtag;
    @doproperty(sequence=29)
    @dovalidationinfo(numericPrecision=10)
    @XmlAttribute()
    public Integer getNrtag() { return m_nrtag; }
    public void setNrtag(Integer value) { m_nrtag = value; }

    public static final String P_ntgew_row = "ntgew_row";
    Float m_ntgew_row;
    @doproperty(sequence=32)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getNtgew_row() { return m_ntgew_row; }
    public void setNtgew_row(Float value) { m_ntgew_row = value; }

    public static final String P_qtaxbag = "qtaxbag";
    Float m_qtaxbag;
    @doproperty(sequence=27)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getQtaxbag() { return m_qtaxbag; }
    public void setQtaxbag(Float value) { m_qtaxbag = value; }

    public static final String P_qtaxtag = "qtaxtag";
    Float m_qtaxtag;
    @doproperty(sequence=25)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getQtaxtag() { return m_qtaxtag; }
    public void setQtaxtag(Float value) { m_qtaxtag = value; }

    public static final String P_reswk = "reswk";
    String m_reswk;
    @doproperty(sequence=8)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getReswk() { return m_reswk; }
    public void setReswk(String value) { m_reswk = value; }

    public static final String P_tag_filler = "tag_filler";
    Float m_tag_filler;
    @doproperty(sequence=28)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getTag_filler() { return m_tag_filler; }
    public void setTag_filler(Float value) { m_tag_filler = value; }

    public static final String P_uzeit = "uzeit";
    LocalTime m_uzeit;
    @doproperty(sequence=34)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalTimeAdapter.class)

    public LocalTime getUzeit() { return m_uzeit; }
    public void setUzeit(LocalTime value) { m_uzeit = value; }

    public static final String P_wamng = "wamng";
    Float m_wamng;
    @doproperty(sequence=20)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getWamng() { return m_wamng; }
    public void setWamng(Float value) { m_wamng = value; }

    public static final String P_wemng = "wemng";
    Float m_wemng;
    @doproperty(sequence=19)
    @dovalidationinfo(numericPrecision=13,numericScale=3)
    @XmlAttribute()
    public Float getWemng() { return m_wemng; }
    public void setWemng(Float value) { m_wemng = value; }

    public static final String P_werks = "werks";
    String m_werks;
    @doproperty(sequence=15)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getWerks() { return m_werks; }
    public void setWerks(String value) { m_werks = value; }

    public static final String P_wmsst = "wmsst";
    String m_wmsst;
    @doproperty(sequence=38)
    @dovalidationinfo(charMaxLength=1)
    @XmlAttribute()
    public String getWmsst() { return m_wmsst; }
    public void setWmsst(String value) { m_wmsst = value; }

    public static final String P_xchpf = "xchpf";
    Boolean m_xchpf;
    @doproperty(sequence=6)
    @XmlAttribute()
    public Boolean getXchpf() { return m_xchpf; }
    public void setXchpf(Boolean value) { m_xchpf = value; }


}