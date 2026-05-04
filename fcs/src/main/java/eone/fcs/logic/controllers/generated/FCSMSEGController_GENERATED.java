package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSMSEG;
import eone.fcs.data.datacontexts.DCFcsmseg;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGController_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSMSEG>
    implements ICCEEConstants
{
    public FCSMSEGController_GENERATED(ENUMEditMode editMode, DCFcsmseg dataContext, DOFCSMSEG bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcsmseg getDataContext() { return (DCFcsmseg)super.getDataContext(); }
    

}