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
@doentity(table="TABFCSLFA1",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="FCSLFA1",dataContextClassName="eone.fcs.data.datacontexts.DCFcslfa1",controllerClassName="eone.fcs.logic.controllers.FCSLFA1Controller",detailUIClassName="eone.fcs.view.dialogs.FCSLFA1Detail",listControllerClassName="eone.fcs.logic.controllers.FCSLFA1ListController",beanGridUIClassName="eone.fcs.view.dialogs.FCSLFA1BeanGrid")

public class DOFCSLFA1
    implements Serializable
{
    public static final String P_lifnr = "lifnr";
    String m_lifnr;
    @doproperty(key=true,sequence=1)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getLifnr() { return m_lifnr; }
    public void setLifnr(String value) { m_lifnr = value; }

    public static final String P_datum = "datum";
    LocalDate m_datum;
    @doproperty(sequence=7)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateAdapter.class)

    public LocalDate getDatum() { return m_datum; }
    public void setDatum(LocalDate value) { m_datum = value; }

    public static final String P_name1 = "name1";
    String m_name1;
    @doproperty(sequence=2)
    @dovalidationinfo(charMaxLength=80)
    @XmlAttribute()
    public String getName1() { return m_name1; }
    public void setName1(String value) { m_name1 = value; }

    public static final String P_name2 = "name2";
    String m_name2;
    @doproperty(sequence=3)
    @dovalidationinfo(charMaxLength=80)
    @XmlAttribute()
    public String getName2() { return m_name2; }
    public void setName2(String value) { m_name2 = value; }

    public static final String P_stcd1 = "stcd1";
    String m_stcd1;
    @doproperty(sequence=4)
    @dovalidationinfo(charMaxLength=16)
    @XmlAttribute()
    public String getStcd1() { return m_stcd1; }
    public void setStcd1(String value) { m_stcd1 = value; }

    public static final String P_stcd2 = "stcd2";
    String m_stcd2;
    @doproperty(sequence=5)
    @dovalidationinfo(charMaxLength=11)
    @XmlAttribute()
    public String getStcd2() { return m_stcd2; }
    public void setStcd2(String value) { m_stcd2 = value; }

    public static final String P_stceg = "stceg";
    String m_stceg;
    @doproperty(sequence=6)
    @dovalidationinfo(charMaxLength=20)
    @XmlAttribute()
    public String getStceg() { return m_stceg; }
    public void setStceg(String value) { m_stceg = value; }

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