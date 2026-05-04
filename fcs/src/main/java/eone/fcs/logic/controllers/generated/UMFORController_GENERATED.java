package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOUMFOR;
import eone.fcs.data.datacontexts.DCUmfor;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMFORController_GENERATED 
    extends BeanInstanceControllerDOFW<DOUMFOR>
    implements ICCEEConstants
{
    public UMFORController_GENERATED(ENUMEditMode editMode, DCUmfor dataContext, DOUMFOR bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCUmfor getDataContext() { return (DCUmfor)super.getDataContext(); }
    

}