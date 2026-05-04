package eone.fcs.data.entities;

import java.io.Serializable;
import java.math.*;
import java.time.*;
import java.util.*;
import org.eclnt.ccee.db.dofw.annotations.*;
import org.eclnt.dataapp.logic.meta.*;
import eone.fcs.data.entities.DOFCST001;

@doentity(table="(SELECT DISTINCT tab.* FROM TABFCST001 tab ) as t",tenantColumn="tenant")
public class VIFCST001
    extends DOFCST001
{

}