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
import eone.fcs.logic.controllers.FCSLFA1Controller;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSLFA1ListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSLFA1,DCDataContext<?>>
{
    public FCSLFA1ListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSLFA1.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSLFA1> readBeans() 
    { 
        return (List<DOFCSLFA1>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSLFA1 createNewItem()
    {
        DOFCSLFA1 bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSLFA1 createNewInstance()
    {
        DOFCSLFA1 bean = new DOFCSLFA1();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSLFA1 bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSLFA1Controller getControllerForItem(DOFCSLFA1 item)
    {
        return (FCSLFA1Controller)super.getControllerForItem(item);
    }
    
    

}
