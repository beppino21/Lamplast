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
import eone.fcs.logic.controllers.FCST001Controller;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCST001ListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCST001,DCDataContext<?>>
{
    public FCST001ListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCST001.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCST001> readBeans() 
    { 
        return (List<DOFCST001>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCST001 createNewItem()
    {
        DOFCST001 bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCST001 createNewInstance()
    {
        DOFCST001 bean = new DOFCST001();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCST001 bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCST001Controller getControllerForItem(DOFCST001 item)
    {
        return (FCST001Controller)super.getControllerForItem(item);
    }
    
    

}
