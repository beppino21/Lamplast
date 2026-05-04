package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSLFA1;
import eone.fcs.data.datacontexts.DCFcslfa1;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSLFA1Controller_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSLFA1>
    implements ICCEEConstants
{
    public FCSLFA1Controller_GENERATED(ENUMEditMode editMode, DCFcslfa1 dataContext, DOFCSLFA1 bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcslfa1 getDataContext() { return (DCFcslfa1)super.getDataContext(); }
    

}