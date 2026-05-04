package eone.fcs.logic.controllers.generated;

import eone.fcs.data.entities.DOFCSEKETHST;
import eone.fcs.data.datacontexts.DCFcsekethst;
import org.eclnt.dataapp.controller.app.BeanInstanceControllerDOFW;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.ccee.ICCEEConstants;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETHSTController_GENERATED 
    extends BeanInstanceControllerDOFW<DOFCSEKETHST>
    implements ICCEEConstants
{
    public FCSEKETHSTController_GENERATED(ENUMEditMode editMode, DCFcsekethst dataContext, DOFCSEKETHST bean)
    {
        super(editMode, dataContext, bean);
    }
    
    public DCFcsekethst getDataContext() { return (DCFcsekethst)super.getDataContext(); }
    

}