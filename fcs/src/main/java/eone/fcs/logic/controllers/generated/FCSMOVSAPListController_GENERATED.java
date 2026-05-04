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
import eone.fcs.logic.controllers.FCSMOVSAPController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSMOVSAP,DCDataContext<?>>
{
    public FCSMOVSAPListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSMOVSAP.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSMOVSAP> readBeans() 
    { 
        return (List<DOFCSMOVSAP>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSMOVSAP createNewItem()
    {
        DOFCSMOVSAP bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSMOVSAP createNewInstance()
    {
        DOFCSMOVSAP bean = new DOFCSMOVSAP();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSMOVSAP bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSMOVSAPController getControllerForItem(DOFCSMOVSAP item)
    {
        return (FCSMOVSAPController)super.getControllerForItem(item);
    }
    
    

}
