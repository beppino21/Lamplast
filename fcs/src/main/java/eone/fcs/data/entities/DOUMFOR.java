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
@doentity(table="TABUMFOR",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="UMFOR",dataContextClassName="eone.fcs.data.datacontexts.DCUmfor",controllerClassName="eone.fcs.logic.controllers.UMFORController",detailUIClassName="eone.fcs.view.dialogs.UMFORDetail",listControllerClassName="eone.fcs.logic.controllers.UMFORListController",beanGridUIClassName="eone.fcs.view.dialogs.UMFORBeanGrid")

public class DOUMFOR
    implements Serializable
{
    public static final String P_bstme = "bstme";
    String m_bstme;
    @doproperty(key=true,sequence=3)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getBstme() { return m_bstme; }
    public void setBstme(String value) { m_bstme = value; }

    public static final String P_datab = "datab";
    LocalDate m_datab;
    @doproperty(key=true,sequence=4)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateAdapter.class)

    public LocalDate getDatab() { return m_datab; }
    public void setDatab(LocalDate value) { m_datab = value; }

    public static final String P_lifnr = "lifnr";
    String m_lifnr;
    @doproperty(key=true,sequence=2)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getLifnr() { return m_lifnr; }
    public void setLifnr(String value) { m_lifnr = value; }

    public static final String P_matnr = "matnr";
    String m_matnr;
    @doproperty(key=true,sequence=1)
    @dovalidationinfo(charMaxLength=18)
    @XmlAttribute()
    public String getMatnr() { return m_matnr; }
    public void setMatnr(String value) { m_matnr = value; }

    public static final String P_bstmexpallet = "bstmexpallet";
    Integer m_bstmexpallet;
    @doproperty(sequence=7)
    @dovalidationinfo(numericPrecision=10)
    @XmlAttribute()
    public Integer getBstmexpallet() { return m_bstmexpallet; }
    public void setBstmexpallet(Integer value) { m_bstmexpallet = value; }

    public static final String P_meins = "meins";
    String m_meins;
    @doproperty(sequence=5)
    @dovalidationinfo(charMaxLength=3)
    @XmlAttribute()
    public String getMeins() { return m_meins; }
    public void setMeins(String value) { m_meins = value; }

    public static final String P_mengexbstme = "mengexbstme";
    Float m_mengexbstme;
    @doproperty(sequence=6)
    @dovalidationinfo(numericPrecision=10,numericScale=3)
    @XmlAttribute()
    public Float getMengexbstme() { return m_mengexbstme; }
    public void setMengexbstme(Float value) { m_mengexbstme = value; }


}