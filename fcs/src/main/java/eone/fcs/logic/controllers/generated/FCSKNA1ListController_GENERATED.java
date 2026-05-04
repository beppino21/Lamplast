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
import eone.fcs.logic.controllers.FCSKNA1Controller;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSKNA1ListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSKNA1,DCDataContext<?>>
{
    public FCSKNA1ListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSKNA1.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSKNA1> readBeans() 
    { 
        return (List<DOFCSKNA1>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSKNA1 createNewItem()
    {
        DOFCSKNA1 bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSKNA1 createNewInstance()
    {
        DOFCSKNA1 bean = new DOFCSKNA1();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSKNA1 bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSKNA1Controller getControllerForItem(DOFCSKNA1 item)
    {
        return (FCSKNA1Controller)super.getControllerForItem(item);
    }
    
    

}
