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
@doentity(isTransient=false,table="TABUMCLI",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="UMCLI",dataContextClassName="eone.fcs.data.datacontexts.DCUmcli",controllerClassName="eone.fcs.logic.controllers.UMCLIController",detailUIClassName="eone.fcs.view.dialogs.UMCLIDetail",listControllerClassName="eone.fcs.logic.controllers.UMCLIListController",beanGridUIClassName="eone.fcs.view.dialogs.UMCLIBeanGrid")

public class DOUMCLI
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

    public static final String P_kunnr = "kunnr";
    String m_kunnr;
    @doproperty(key=true,sequence=2)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getKunnr() { return m_kunnr; }
    public void setKunnr(String value) { m_kunnr = value; }

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
    @doproperty(key=false,sequence=5)
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