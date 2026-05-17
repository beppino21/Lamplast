package eone.fcs.data.entities;

import java.io.Serializable;
import java.math.*;
import java.time.*;
import java.util.*;
import org.eclnt.ccee.db.dofw.annotations.*;
import org.eclnt.dataapp.logic.meta.*;
import eone.fcs.data.entities.DOFCSRESTLOG;

@doentity(isTransient=false,table="(SELECT DISTINCT tab.* FROM TABFCSRESTLOG tab ) as t",tenantColumn="tenant")
public class VIFCSRESTLOG
    extends DOFCSRESTLOG
{

}