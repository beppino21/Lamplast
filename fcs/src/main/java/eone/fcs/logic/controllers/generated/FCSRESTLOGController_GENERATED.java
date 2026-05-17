package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSRESTLOG;
import eone.fcs.data.datacontexts.DCFcsrestlog;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSRESTLOGController_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSRESTLOG>
    implements ICCEEConstants
{
    public FCSRESTLOGController_GENERATED(ENUMEditMode editMode, DCFcsrestlog dataContext, DOFCSRESTLOG bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcsrestlog getDataContext() { return (DCFcsrestlog)super.getDataContext(); }
    

}