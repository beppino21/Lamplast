package eone.fcs.logic.controllers.generated;

import java.util.List;

import org.eclnt.ccee.datacontext.*;
import org.eclnt.dataapp.logic.entities.datacontext.*;
import org.eclnt.dataapp.controller.app.IBeanInstanceController;
import org.eclnt.dataapp.view.app.ENUMEditMode;

import org.eclnt.dataapp.controller.app.BeanListControllerBaseDOFW;
import eone.fcs.data.entities.*;
import eone.fcs.data.datacontexts.*;
import eone.fcs.logic.controllers.*;
import eone.fcs.logic.controllers.FCSMOVSAPHSTController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPHSTListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSMOVSAPHST,DCDataContext<?>>
{
    public FCSMOVSAPHSTListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSMOVSAPHST.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSMOVSAPHST> readBeans() 
    { 
        return (List<DOFCSMOVSAPHST>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSMOVSAPHST createNewItem()
    {
        DOFCSMOVSAPHST bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSMOVSAPHST createNewInstance()
    {
        DOFCSMOVSAPHST bean = new DOFCSMOVSAPHST();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSMOVSAPHST bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSMOVSAPHSTController getControllerForItem(DOFCSMOVSAPHST item)
    {
        return (FCSMOVSAPHSTController)super.getControllerForItem(item);
    }
    
    

}
