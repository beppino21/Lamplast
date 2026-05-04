package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOUMCLI;
import eone.fcs.data.datacontexts.DCUmcli;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMCLIController_GENERATED 
    extends BeanInstanceControllerDOFW<DOUMCLI>
    implements ICCEEConstants
{
    public UMCLIController_GENERATED(ENUMEditMode editMode, DCUmcli dataContext, DOUMCLI bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCUmcli getDataContext() { return (DCUmcli)super.getDataContext(); }
    

}