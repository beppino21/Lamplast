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
@doentity(table="TABFCSMARA",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="FCSMARA",dataContextClassName="eone.fcs.data.datacontexts.DCFcsmara",controllerClassName="eone.fcs.logic.controllers.FCSMARAController",detailUIClassName="eone.fcs.view.dialogs.FCSMARADetail",listControllerClassName="eone.fcs.logic.controllers.FCSMARAListController",beanGridUIClassName="eone.fcs.view.dialogs.FCSMARABeanGrid")

public class DOFCSMARA
    implements Serializable
{
    public static final String P_matnr = "matnr";
    String m_matnr;
    @doproperty(key=true,sequence=1)
    @dovalidationinfo(charMaxLength=18)
    @XmlAttribute()
    public String getMatnr() { return m_matnr; }
    public void setMatnr(String value) { m_matnr = value; }

    public static final String P_bstme = "bstme";
    String m_bstme;
    @doproperty(sequence=6)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getBstme() { return m_bstme; }
    public void setBstme(String value) { m_bstme = value; }

    public static final String P_datum = "datum";
    LocalDate m_datum;
    @doproperty(sequence=7)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateAdapter.class)

    public LocalDate getDatum() { return m_datum; }
    public void setDatum(LocalDate value) { m_datum = value; }

    public static final String P_maktx = "maktx";
    String m_maktx;
    @doproperty(sequence=2)
    @dovalidationinfo(charMaxLength=40)
    @XmlAttribute()
    public String getMaktx() { return m_maktx; }
    public void setMaktx(String value) { m_maktx = value; }

    public static final String P_matkl = "matkl";
    String m_matkl;
    @doproperty(sequence=4)
    @dovalidationinfo(charMaxLength=9)
    @XmlAttribute()
    public String getMatkl() { return m_matkl; }
    public void setMatkl(String value) { m_matkl = value; }

    public static final String P_meins = "meins";
    String m_meins;
    @doproperty(sequence=5)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getMeins() { return m_meins; }
    public void setMeins(String value) { m_meins = value; }

    public static final String P_mtart = "mtart";
    String m_mtart;
    @doproperty(sequence=3)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getMtart() { return m_mtart; }
    public void setMtart(String value) { m_mtart = value; }

    public static final String P_uname = "uname";
    String m_uname;
    @doproperty(sequence=9)
    @dovalidationinfo(charMaxLength=12)
    @XmlAttribute()
    public String getUname() { return m_uname; }
    public void setUname(String value) { m_uname = value; }

    public static final String P_updfl = "updfl";
    Boolean m_updfl;
    @doproperty(sequence=10)
    @XmlAttribute()
    public Boolean getUpdfl() { return m_updfl; }
    public void setUpdfl(Boolean value) { m_updfl = value; }

    public static final String P_uzeit = "uzeit";
    LocalTime m_uzeit;
    @doproperty(sequence=8)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalTimeAdapter.class)

    public LocalTime getUzeit() { return m_uzeit; }
    public void setUzeit(LocalTime value) { m_uzeit = value; }


}