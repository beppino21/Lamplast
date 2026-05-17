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
@doentity(isTransient=false,table="TABFCSRESTLOG",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="FCSRESTLOG",dataContextClassName="eone.fcs.data.datacontexts.DCFcsrestlog",controllerClassName="eone.fcs.logic.controllers.FCSRESTLOGController",detailUIClassName="eone.fcs.view.dialogs.FCSRESTLOGDetail",listControllerClassName="eone.fcs.logic.controllers.FCSRESTLOGListController",beanGridUIClassName="eone.fcs.view.dialogs.FCSRESTLOGBeanGrid")

public class DOFCSRESTLOG
    implements Serializable
{
    public static final String P_prog = "prog";
    int m_prog;
    @doproperty(key=true,sequence=2)
    @XmlAttribute()
    public int getProg() { return m_prog; }
    public void setProg(int value) { m_prog = value; }

    public static final String P_zdatetime = "zdatetime";
    LocalDateTime m_zdatetime;
    @doproperty(key=true,sequence=1)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateTimeAdapter.class)

    public LocalDateTime getZdatetime() { return m_zdatetime; }
    public void setZdatetime(LocalDateTime value) { m_zdatetime = value; }

    public static final String P_http_method = "http_method";
    String m_http_method;
    @doproperty(sequence=3)
    @dovalidationinfo(charMaxLength=50,convertEmptyToNull=true)
    @XmlAttribute()
    public String getHttp_method() { return m_http_method; }
    public void setHttp_method(String value) { m_http_method = value; }

    public static final String P_id_eket = "id_eket";
    String m_id_eket;
    @doproperty(sequence=6)
    @dovalidationinfo(charMaxLength=24)
    @XmlAttribute()
    public String getId_eket() { return m_id_eket; }
    public void setId_eket(String value) { m_id_eket = value; }

    public static final String P_movid = "movid";
    String m_movid;
    @doproperty(sequence=7)
    @dovalidationinfo(charMaxLength=50,convertEmptyToNull=true)
    @XmlAttribute()
    public String getMovid() { return m_movid; }
    public void setMovid(String value) { m_movid = value; }

    public static final String P_query_string = "query_string";
    String m_query_string;
    @doproperty(sequence=4)
    @dovalidationinfo(charMaxLength=500,convertEmptyToNull=true)
    @XmlAttribute()
    public String getQuery_string() { return m_query_string; }
    public void setQuery_string(String value) { m_query_string = value; }

    public static final String P_response = "response";
    String m_response;
    @doproperty(sequence=5)
    @dovalidationinfo(charMaxLength=500,convertEmptyToNull=true)
    @XmlAttribute()
    public String getResponse() { return m_response; }
    public void setResponse(String value) { m_response = value; }


}