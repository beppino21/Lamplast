package eone.fcs.data.datacontexts.generated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.eclnt.ccee.ICCEEConstants;
import org.eclnt.ccee.db.dofw.DOFWSql;
import org.eclnt.dataapp.logic.entities.datacontext.DCKey;
import org.eclnt.ccee.datacontext.*;

import eone.fcs.data.entities.*;
import eone.fcs.data.entities.*;
import org.eclnt.dataapp.meta.data.entities.*;
import org.eclnt.ccda_base.data.entities.*;
import org.eclnt.ccda_clog.data.entities.*;


/**
 * This class is generated. Do not change any content - it will be
 * overridden with the next generation!
 */
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class DCFcssync_GENERATED
    extends org.eclnt.dataapp.logic.entities.datacontext.DCDataContext<DOFCSSYNC>
    implements Serializable, ICCEEConstants
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------
    
    private static final Object NOTCOVERED_BYGENERATION = new Object();
     
    
    
    public static final DCKey DCKEY_DOFCSSYNC = new DCKey(DOFCSSYNC.class);


    // ------------------------------------------------------------------------
    // constructor
    // ------------------------------------------------------------------------
    
    public DCFcssync_GENERATED(DOFCSSYNC homeObject) { super(homeObject); }
    public DCFcssync_GENERATED(String dbContextName, DOFCSSYNC homeObject) { super(dbContextName,homeObject); }

    // ------------------------------------------------------------------------
    // public access
    // ------------------------------------------------------------------------
    
    protected DCKey getHomeObjectContentType() { return DCKEY_DOFCSSYNC; }
    
    @Override
    public DCKey[] getAllContainedContentTypes()
    {
        return new DCKey[]
        {
        };
    }








    @Override
    protected final Object readContent(DCKey contentType)
    {
        if (getHomeObjectContentType().equals(contentType))
            return getHomeObject();
        Object result = readContentGenerated(contentType);
        if (result == NOTCOVERED_BYGENERATION)
            result = readContentImpl(contentType);
        return result;
    }
    protected Object readContentImpl(DCKey contentType) { throw new Error("Missing implementation of method readContentImpl in data context implementation."); }

    private Object readContentGenerated(DCKey contentType)
    {
        return NOTCOVERED_BYGENERATION;
    }

    @Override
    public void dbDeleteAssociatedObjects()
    {
    }





    @Override
    public void updateKeysOfContainedObjects()
    {
    }


}
