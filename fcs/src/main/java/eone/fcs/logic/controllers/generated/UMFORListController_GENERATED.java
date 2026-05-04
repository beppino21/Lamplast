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
import eone.fcs.logic.controllers.UMFORController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMFORListController_GENERATED
    extends BeanListControllerBaseDOFW<DOUMFOR,DCDataContext<?>>
{
    public UMFORListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOUMFOR.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOUMFOR> readBeans() 
    { 
        return (List<DOUMFOR>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOUMFOR createNewItem()
    {
        DOUMFOR bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOUMFOR createNewInstance()
    {
        DOUMFOR bean = new DOUMFOR();
        return bean;
    }
    
    @Override
    public void removeBean(DOUMFOR bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected UMFORController getControllerForItem(DOUMFOR item)
    {
        return (UMFORController)super.getControllerForItem(item);
    }
    
    

}
