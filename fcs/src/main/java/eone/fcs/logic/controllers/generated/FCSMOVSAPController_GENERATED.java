package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSMOVSAP;
import eone.fcs.data.datacontexts.DCFcsmovsap;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPController_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSMOVSAP>
    implements ICCEEConstants
{
    public FCSMOVSAPController_GENERATED(ENUMEditMode editMode, DCFcsmovsap dataContext, DOFCSMOVSAP bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcsmovsap getDataContext() { return (DCFcsmovsap)super.getDataContext(); }
    

}