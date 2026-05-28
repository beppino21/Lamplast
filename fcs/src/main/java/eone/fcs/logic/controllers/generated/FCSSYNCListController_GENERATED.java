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
import eone.fcs.logic.controllers.FCSSYNCController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSSYNCListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSSYNC,DCDataContext<?>>
{
    public FCSSYNCListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSSYNC.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSSYNC> readBeans() 
    { 
        return (List<DOFCSSYNC>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSSYNC createNewItem()
    {
        DOFCSSYNC bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSSYNC createNewInstance()
    {
        DOFCSSYNC bean = new DOFCSSYNC();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSSYNC bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSSYNCController getControllerForItem(DOFCSSYNC item)
    {
        return (FCSSYNCController)super.getControllerForItem(item);
    }
    
    

}
