package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSKNA1;
import eone.fcs.data.datacontexts.DCFcskna1;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSKNA1Controller_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSKNA1>
    implements ICCEEConstants
{
    public FCSKNA1Controller_GENERATED(ENUMEditMode editMode, DCFcskna1 dataContext, DOFCSKNA1 bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcskna1 getDataContext() { return (DCFcskna1)super.getDataContext(); }
    

}