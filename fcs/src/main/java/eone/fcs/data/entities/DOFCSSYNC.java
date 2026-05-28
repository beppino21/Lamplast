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
@doentity(isTransient=false,table="TABFCSSYNC",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="FCSSYNC",dataContextClassName="eone.fcs.data.datacontexts.DCFcssync",controllerClassName="eone.fcs.logic.controllers.FCSSYNCController",detailUIClassName="eone.fcs.view.dialogs.FCSSYNCDetail",listControllerClassName="eone.fcs.logic.controllers.FCSSYNCListController",beanGridUIClassName="eone.fcs.view.dialogs.FCSSYNCBeanGrid")

public class DOFCSSYNC
    implements Serializable
{
    public static final String P_entity = "entity";
    String m_entity;
    @doproperty(key=true,sequence=1)
    @dovalidationinfo(charMaxLength=30,mandatory=true)
    @XmlAttribute()
    public String getEntity() { return m_entity; }
    public void setEntity(String value) { m_entity = value; }

    public static final String P_last_count = "last_count";
    Integer m_last_count;
    @doproperty(sequence=4)
    @XmlAttribute()
    public Integer getLast_count() { return m_last_count; }
    public void setLast_count(Integer value) { m_last_count = value; }

    public static final String P_last_error = "last_error";
    String m_last_error;
    @doproperty(sequence=8)
    @dovalidationinfo(charMaxLength=500)
    @XmlAttribute()
    public String getLast_error() { return m_last_error; }
    public void setLast_error(String value) { m_last_error = value; }

    public static final String P_last_status = "last_status";
    String m_last_status;
    @doproperty(sequence=6)
    @dovalidationinfo(charMaxLength=10)
    @XmlAttribute()
    public String getLast_status() { return m_last_status; }
    public void setLast_status(String value) { m_last_status = value; }

    public static final String P_last_sync = "last_sync";
    LocalDateTime m_last_sync;
    @doproperty(sequence=2)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateTimeAdapter.class)

    public LocalDateTime getLast_sync() { return m_last_sync; }
    public void setLast_sync(LocalDateTime value) { m_last_sync = value; }

    public static final String P_last_upserted = "last_upserted";
    Integer m_last_upserted;
    @doproperty(sequence=5)
    @XmlAttribute()
    public Integer getLast_upserted() { return m_last_upserted; }
    public void setLast_upserted(Integer value) { m_last_upserted = value; }

    public static final String P_next_sync_from = "next_sync_from";
    LocalDateTime m_next_sync_from;
    @doproperty(sequence=3)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateTimeAdapter.class)

    public LocalDateTime getNext_sync_from() { return m_next_sync_from; }
    public void setNext_sync_from(LocalDateTime value) { m_next_sync_from = value; }

    public static final String P_created_at = "created_at";
    LocalDateTime m_created_at;
    @doproperty(sequence=9)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateTimeAdapter.class)

    public LocalDateTime getCreated_at() { return m_created_at; }
    public void setCreated_at(LocalDateTime value) { m_created_at = value; }

    public static final String P_updated_at = "updated_at";
    LocalDateTime m_updated_at;
    @doproperty(sequence=10)
    @XmlAttribute() @XmlJavaTypeAdapter(LocalDateTimeAdapter.class)

    public LocalDateTime getUpdated_at() { return m_updated_at; }
    public void setUpdated_at(LocalDateTime value) { m_updated_at = value; }

    public static final String P_last_duration_ms = "last_duration_ms";
    long m_last_duration_ms;
    @doproperty(sequence=7)
    @XmlAttribute()
    public long getLast_duration_ms() { return m_last_duration_ms; }
    public void setLast_duration_ms(long value) { m_last_duration_ms = value; }


}