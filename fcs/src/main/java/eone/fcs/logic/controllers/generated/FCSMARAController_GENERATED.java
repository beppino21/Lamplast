package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSMARA;
import eone.fcs.data.datacontexts.DCFcsmara;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMARAController_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSMARA>
    implements ICCEEConstants
{
    public FCSMARAController_GENERATED(ENUMEditMode editMode, DCFcsmara dataContext, DOFCSMARA bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcsmara getDataContext() { return (DCFcsmara)super.getDataContext(); }
    

}