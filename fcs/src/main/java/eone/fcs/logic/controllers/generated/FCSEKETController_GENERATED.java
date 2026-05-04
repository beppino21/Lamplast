package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSEKET;
import eone.fcs.data.datacontexts.DCFcseket;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETController_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSEKET>
    implements ICCEEConstants
{
    public FCSEKETController_GENERATED(ENUMEditMode editMode, DCFcseket dataContext, DOFCSEKET bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcseket getDataContext() { return (DCFcseket)super.getDataContext(); }
    

}