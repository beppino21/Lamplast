package eone.fcs.data.entities;

import java.io.Serializable;
import java.math.*;
import java.time.*;
import java.util.*;
import org.eclnt.ccee.db.dofw.annotations.*;
import org.eclnt.dataapp.logic.meta.*;
import eone.fcs.data.entities.DOUMFOR;

@doentity(table="(SELECT DISTINCT tab.* FROM TABUMFOR tab ) as t",tenantColumn="tenant")
public class VIUMFOR
    extends DOUMFOR
{

}