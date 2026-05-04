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
import eone.fcs.logic.controllers.FCSMSEGController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSMSEG,DCDataContext<?>>
{
    public FCSMSEGListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSMSEG.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSMSEG> readBeans() 
    { 
        return (List<DOFCSMSEG>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSMSEG createNewItem()
    {
        DOFCSMSEG bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSMSEG createNewInstance()
    {
        DOFCSMSEG bean = new DOFCSMSEG();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSMSEG bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSMSEGController getControllerForItem(DOFCSMSEG item)
    {
        return (FCSMSEGController)super.getControllerForItem(item);
    }
    
    

}
