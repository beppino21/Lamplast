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
@doentity(table="TABFCST001",tenantColumn="tenant")
@doclassmapping(metaDataId="fcs",entityId="FCST001",dataContextClassName="eone.fcs.data.datacontexts.DCFcst001",controllerClassName="eone.fcs.logic.controllers.FCST001Controller",detailUIClassName="eone.fcs.view.dialogs.FCST001Detail",listControllerClassName="eone.fcs.logic.controllers.FCST001ListController",beanGridUIClassName="eone.fcs.view.dialogs.FCST001BeanGrid")

public class DOFCST001
    implements Serializable
{
    public static final String P_mtart = "mtart";
    String m_mtart;
    @doproperty(key=true,sequence=1)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getMtart() { return m_mtart; }
    public void setMtart(String value) { m_mtart = value; }

    public static final String P_werks = "werks";
    String m_werks;
    @doproperty(key=true,sequence=2)
    @dovalidationinfo(charMaxLength=4)
    @XmlAttribute()
    public String getWerks() { return m_werks; }
    public void setWerks(String value) { m_werks = value; }

    public static final String P_exp2fcs = "exp2fcs";
    Boolean m_exp2fcs;
    @doproperty(sequence=3)
    @XmlAttribute()
    public Boolean getExp2fcs() { return m_exp2fcs; }
    public void setExp2fcs(Boolean value) { m_exp2fcs = value; }


}