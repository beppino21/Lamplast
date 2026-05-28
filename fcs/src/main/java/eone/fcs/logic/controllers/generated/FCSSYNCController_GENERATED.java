package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSSYNC;
import eone.fcs.data.datacontexts.DCFcssync;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSSYNCController_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSSYNC>
    implements ICCEEConstants
{
    public FCSSYNCController_GENERATED(ENUMEditMode editMode, DCFcssync dataContext, DOFCSSYNC bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcssync getDataContext() { return (DCFcssync)super.getDataContext(); }
    

}