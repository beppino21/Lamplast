package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCST001;
import eone.fcs.data.datacontexts.DCFcst001;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCST001Controller_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCST001>
    implements ICCEEConstants
{
    public FCST001Controller_GENERATED(ENUMEditMode editMode, DCFcst001 dataContext, DOFCST001 bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcst001 getDataContext() { return (DCFcst001)super.getDataContext(); }
    

}