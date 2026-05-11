package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSMOVSAPHST;
import eone.fcs.data.datacontexts.DCFcsmovsaphst;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPHSTController_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSMOVSAPHST>
    implements ICCEEConstants
{
    public FCSMOVSAPHSTController_GENERATED(ENUMEditMode editMode, DCFcsmovsaphst dataContext, DOFCSMOVSAPHST bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcsmovsaphst getDataContext() { return (DCFcsmovsaphst)super.getDataContext(); }
    

}