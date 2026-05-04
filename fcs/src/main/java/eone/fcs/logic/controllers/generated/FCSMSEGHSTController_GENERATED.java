package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSMSEGHST;
import eone.fcs.data.datacontexts.DCFcsmseghst;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGHSTController_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSMSEGHST>
    implements ICCEEConstants
{
    public FCSMSEGHSTController_GENERATED(ENUMEditMode editMode, DCFcsmseghst dataContext, DOFCSMSEGHST bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcsmseghst getDataContext() { return (DCFcsmseghst)super.getDataContext(); }
    

}