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
import eone.fcs.logic.controllers.FCSRESTLOGController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSRESTLOGListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSRESTLOG,DCDataContext<?>>
{
    public FCSRESTLOGListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSRESTLOG.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSRESTLOG> readBeans() 
    { 
        return (List<DOFCSRESTLOG>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSRESTLOG createNewItem()
    {
        DOFCSRESTLOG bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSRESTLOG createNewInstance()
    {
        DOFCSRESTLOG bean = new DOFCSRESTLOG();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSRESTLOG bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSRESTLOGController getControllerForItem(DOFCSRESTLOG item)
    {
        return (FCSRESTLOGController)super.getControllerForItem(item);
    }
    
    

}
