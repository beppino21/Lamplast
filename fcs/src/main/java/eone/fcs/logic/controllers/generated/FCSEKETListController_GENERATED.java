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
import eone.fcs.logic.controllers.FCSEKETController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSEKET,DCDataContext<?>>
{
    public FCSEKETListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSEKET.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSEKET> readBeans() 
    { 
        return (List<DOFCSEKET>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSEKET createNewItem()
    {
        DOFCSEKET bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSEKET createNewInstance()
    {
        DOFCSEKET bean = new DOFCSEKET();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSEKET bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSEKETController getControllerForItem(DOFCSEKET item)
    {
        return (FCSEKETController)super.getControllerForItem(item);
    }
    
    

}
